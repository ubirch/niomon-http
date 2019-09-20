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
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import com.ubirch.receiver.actors.{RequestData, ResponseData}
import com.ubirch.receiver.http.HttpServer._
import io.prometheus.client.{Counter, Summary}
import net.logstash.logback.argument.StructuredArguments.v

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
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
        .in(docHeader("X-Ubirch-HardwareId", "the hardware id of the sender device"))
        .in(docHeader("X-Ubirch-Auth-Type", "auth type",
          Validator.enum(List("cumulocity", "ubirch", "keycloak").map(Some(_)) :+ None)))
        .in(docHeader("X-Ubirch-Credential", "checked for ubirch auth"))
        .in(docHeader("Authorization", cumulocityAuthDocs))
        .in(docHeader("X-XSRF-TOKEN", cumulocityAuthDocs))
        .in(docHeader("X-Cumulocity-BaseUrl", "change which cumulocity instance is asked for auth"))
        .in(docHeader("X-Cumulocity-Tenant", "change which cumulocity tenant is asked for auth"))
        .in(docHeader("X-Niomon-Purge-Caches", "set this header to clean niomon caches (dangerous)"))
        .errorOut(stringBody.description("error details").and(statusCode(500)))
        .out(binaryBody[Array[Byte]].description("arbitrary response, configurable per device; status code may vary"))
        .out(statusCode)
        .out(header[String]("Content-Type").description("actual content type of the response (sadly this cannot be modelled accurately in swagger)"))
    }

    val route: Route = {
      import tapir.server.akkahttp._
      endpointDescription.toRoute { tup =>
        // this is like this, because this is a 12-element tuple
        val h = tup._1
        val authCookie = tup._2
        val requestUri = tup._3
        val input = tup._4

        requestReceived.inc()
        val timer = processingTimer.startTimer()
        val requestId = UUID.randomUUID().toString
        val headers = getHeaders(h, authCookie, requestUri)
        log.info(s"HTTP request: ${v("requestId", requestId)} [${v("headers", headers.asJava)}]")
        val responseData = dispatcher ? RequestData(requestId, input, headers)
        responseData.transform {
          case Success(res) =>
            val result = res.asInstanceOf[ResponseData]
            // ToDo BjB 21.09.18 : Revise Headers
            val headers = result.headers
            val contentType = determineContentType(headers)
            val status = headers.get("http-status-code").map(_.toInt: StatusCode).getOrElse(StatusCodes.OK)
            responsesSent.labels(status.toString()).inc()
            timer.observeDuration()
            Success(Right((result.data, status.intValue(), contentType.toString())))
          case Failure(e) =>
            log.error("dispatcher failure", e)
            responsesSent.labels(StatusCodes.InternalServerError.toString()).inc()
            timer.observeDuration()
            Success(Left(e.getMessage))
        }
      } ~ get {
        path("status") {
          complete("up")
        } ~ pathPrefix("swagger") {
          path("swagger.json") {
            import tapir.docs.openapi._
            import tapir.openapi.circe.yaml._
            respondWithHeader(`Content-Type`(MediaType.applicationWithFixedCharset("x-yaml", HttpCharsets.`UTF-8`)))(
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

  private def determineContentType(headers: Map[String, String]) = {
    ContentType.parse(headers.getOrElse(`Content-Type`.name, "")) match {
      case Left(_) => ContentTypes.`application/octet-stream`
      case Right(x) => x
    }
  }

  private val HEADERS_TO_PRESERVE = Array( // excludes Cookie header, because we only want one specific cookie
    "Content-Type",
    "Authorization",
    "X-XSRF-TOKEN",
    "X-Cumulocity-BaseUrl",
    "X-Cumulocity-Tenant",
    "X-Niomon-Purge-Caches"
  ).map(_.toLowerCase)

  private def getHeaders(headers: Seq[(String, String)], authCookie: Option[String], requestUri: URI): Map[String, String] = {
    val headersToPreserve = headers.filter { case (key, _) =>
      HEADERS_TO_PRESERVE.contains(key.toLowerCase())
    } ++ authCookie.map(v => "Cookie" -> s"authorization=$v")

    Map("Request-URI" -> requestUri.toString) ++ headersToPreserve
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
    .build("processing_time", "Message processing time in seconds")
    .register()
}
