/*
 * Copyright (c) 2019 ubirch GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ubirch.receiver.http

import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.{AskTimeoutException, ask}
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import com.ubirch.receiver.actors.{RequestData, ResponseData}
import com.ubirch.receiver.conf.HeaderKeys
import com.ubirch.receiver.http.HttpServer._
import io.prometheus.client.{Counter, Summary}
import net.logstash.logback.argument.StructuredArguments.v

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{Failure, Success}

class HttpServer(port: Int, dispatcher: ActorRef)(implicit val system: ActorSystem) {

  val log: Logger = Logger[HttpServer]
  implicit val context: ExecutionContext = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(30, TimeUnit.SECONDS) // scalastyle:off magic.number

  def serveHttp(): Unit = {
    val endpointDescription = {
      import tapir._
      // just for documentation purposes, the header is ignored
      def docHeader(name: String, doc: String, v: Validator[Option[String]] = Validator.pass): EndpointInput[Option[String]] =
        header[Option[String]](name)
          .description(doc)
          .validate(v)
      //          .map(_ => ())(_ => None) // for some reason this breaks akka server

      val cumulocityAuthDocs = "checked for cumulocity auth (only one of {Authorization (header), " +
        "authorization (cookie), X-XSRF-TOKEN (header)} needed)"

      endpoint
        .post
        .description("Anchors the given Ubirch Protocol Packet (passed in as the body, I don't know why swagger ui doesn't show body's description)")
        .in(headers.description("some headers are propagated further into niomon system... TODO: explain which exactly"))
        .in(cookie[Option[String]]("authorization").description(cumulocityAuthDocs))
        .in(extractFromRequest(req => req.uri))
        .in(binaryBody[Array[Byte]].description("Ubirch Protocol Packet to be anchored"))
        .in(docHeader(HeaderKeys.XUBIRCHHARDWAREID, "the hardware id of the sender device"))
        .in(docHeader(HeaderKeys.XUBIRCHAUTHTYPE, "auth type", Validator.enum(List("cumulocity", "ubirch", "keycloak").map(Some(_)) :+ None)))
        .in(docHeader(HeaderKeys.XUBIRCHCREDENTIAL, "checked for ubirch auth"))
        .in(docHeader(HeaderKeys.AUTHORIZATION, cumulocityAuthDocs))
        .in(docHeader(HeaderKeys.XXSRFTOKEN, cumulocityAuthDocs))
        .in(docHeader(HeaderKeys.XCUMULOCITYBASEURL, "change which cumulocity instance is asked for auth"))
        .in(docHeader(HeaderKeys.XCUMULOCITYTENANT, "change which cumulocity tenant is asked for auth"))
        .errorOut(stringBody.description("error details").and(statusCode(500)))
        .out(binaryBody[Array[Byte]].description("arbitrary response, configurable per device; status code may vary"))
        .out(statusCode)
    }

    val route: Route = {
      import tapir.server.akkahttp._
      endpointDescription.toRoute { tup =>
        // this is like this, because this is a 10-element tuple
        val h = tup._1
        val authCookie = tup._2
        val requestUri = tup._3
        val input = tup._4

        requestReceived.inc()
        val timer = processingTimer.startTimer()
        val requestId = UUID.randomUUID().toString
        val headers = getHeaders(h, authCookie, requestUri)
        log.debug(s"HTTP request: {} [{}]", v("requestId", requestId), v("headers", headers.asJava))
        (dispatcher ? RequestData(requestId, input, headers)).transform {
          case Success(result: ResponseData) =>
            // ToDo BjB 21.09.18 : Revise Headers
            val headers = result.headers
            val status = headers.get("http-status-code").map(_.toInt: StatusCode).getOrElse(StatusCodes.OK)
            responsesSent.labels(status.toString()).inc()
            timer.observeDuration()
            Success(Right((result.data, status.intValue())))

          case Success(_) =>
            log.error("dispatcher failure -wrong response type-", v("requestId", requestId))
            responsesSent.labels(StatusCodes.InternalServerError.toString()).inc()
            timer.observeDuration()
            Success(Left(s"The request[$requestId] was successfully processed but couldn't be fully completed as response"))

          case Failure(e:AskTimeoutException) =>
            log.error(s"dispatcher failure -timeout-: ${e.getMessage}", v("requestId", requestId))
            responsesSent.labels(StatusCodes.InternalServerError.toString()).inc()
            timer.observeDuration()
            Success(Left(s"The request[$requestId] timed out. Try again."))

          case Failure(e) =>
            log.error(s"dispatcher failure: ${e.getMessage}", v("requestId", requestId))
            responsesSent.labels(StatusCodes.InternalServerError.toString()).inc()
            timer.observeDuration()
            Success(Left(s"The request[$requestId] couldn't be successfully processed. Try again."))

        }
      } ~ get {
        path("status") {
          complete("up")
        } ~ pathPrefix("swagger") {
          path("swagger.json") {
            import tapir.docs.openapi._
            import tapir.openapi.circe.yaml._
            respondWithHeaders(`Content-Type`(MediaType.applicationWithFixedCharset("x-yaml", HttpCharsets.`UTF-8`)), `Access-Control-Allow-Origin` *)(
              complete(endpointDescription.toOpenAPI("Niomon HTTP", "1.0.1-SNAPSHOT").toYaml)
            )
          } ~ getFromResourceDirectory("swagger") ~ redirectToTrailingSlashIfMissing(StatusCodes.MovedPermanently) {
            pathSingleSlash {
              getFromResource("swagger/index.html")
            }
          }
        }
      }
    }

    Http().bindAndHandle(route, "0.0.0.0", port) onComplete {
      case Success(v) => log.info(s"http server started: ${v.localAddress}")
      case Failure(e) => log.error("http server start failed", e)
    }
    // ToDo BjB 17.09.18 : Graceful shutdown

  }

  private val HEADERS_TO_PRESERVE = Array( // excludes Cookie header, because we only want one specific cookie
    HeaderKeys.CONTENTTYPE,
    HeaderKeys.AUTHORIZATION,
    HeaderKeys.XXSRFTOKEN,
    HeaderKeys.XCUMULOCITYBASEURL,
    HeaderKeys.XCUMULOCITYTENANT,
    HeaderKeys.XUBIRCHCREDENTIAL,
    HeaderKeys.XUBIRCHHARDWAREID,
    HeaderKeys.XUBIRCHAUTHTYPE
  ).map(_.toLowerCase)

  private def getHeaders(headers: Seq[(String, String)], authCookie: Option[String], requestUri: URI): Map[String, String] = {
    val headersToPreserve = headers.filter { case (key, _) =>
      HEADERS_TO_PRESERVE.contains(key.toLowerCase())
    } ++ authCookie.map(v => "Cookie" -> s"authorization=$v")

    val r = Map("Request-URI" -> requestUri.toString) ++ headersToPreserve
    r.map { h => h._1.toLowerCase -> h._2 }
  }
}

object HttpServer {
  val requestReceived: Counter = Counter
    .build("http_requests_count", "Number of http request received.")
    .register()

  val responsesSent: Counter = Counter
    .build("http_responses_count", "Number of http responses sent.")
    .labelNames("status")
    .register()

  val processingTimer: Summary = Summary
    .build("processing_time_seconds", "Message processing time in seconds")
    .quantile(0.9, 0.05)
    .quantile(0.95, 0.05)
    .quantile(0.99, 0.05)
    .quantile(0.999, 0.05)
    .register()
}
