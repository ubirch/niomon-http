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
import com.ubirch.receiver.http.HttpServer.{requestReceived, responsesSent}
import io.prometheus.client.Counter

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class HttpServer(port: Int, dispatcher: ActorRef)(implicit val system: ActorSystem) {

  val log: Logger = Logger[HttpServer]
  implicit val context: ExecutionContext = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(30, TimeUnit.SECONDS) // scalastyle:off magic.number

  def serveHttp(): Unit = {
    val route: Route = {
      post {
        pathEndOrSingleSlash {
          extractRequest {
            req =>
              entity(as[Array[Byte]]) {
                input =>
                  requestReceived.inc()
                  val requestId = UUID.randomUUID().toString
                  log.debug(s"HTTP request-id $requestId")
                  val headers = getHeaders(req)
                  log.debug(s"all HTTP headers: [${req.headers}]")
                  log.debug(s"HTTP headers to forward to kafka: [$headers]")
                  val responseData = dispatcher ? RequestData(requestId, input, headers)
                  onComplete(responseData) {
                    case Success(res) =>
                      val result = res.asInstanceOf[ResponseData]
                      // ToDo BjB 21.09.18 : Revise Headers
                      val headers = result.headers
                      val contentType = determineContentType(headers)
                      val status = headers.get("http-status-code").map(_.toInt: StatusCode).getOrElse(StatusCodes.OK)
                      responsesSent.labels(status.toString()).inc()
                      complete(HttpResponse(status = status, entity = HttpEntity(contentType, result.data)))
                    case Failure(e) =>
                      log.debug("dispatcher failure", e)
                      responsesSent.labels(StatusCodes.InternalServerError.toString()).inc()
                      complete((StatusCodes.InternalServerError, e.getMessage))
                  }
              }
          }
        }
      } ~
        get {
          path("status") {
            complete("up")
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
  )

  private def getHeaders(req: HttpRequest): Map[String, String] = {
    val headersToPreserve = req.headers.filter { h =>
      HEADERS_TO_PRESERVE.contains(h.name())
    } ++ req.header[Cookie].flatMap { c => c.cookies.find(_.name == "authorization").map(Cookie(_)) }

    Map("Request-URI" -> req.uri.toString) ++ headersToPreserve.map(h => h.name -> h.value)
  }
}

object HttpServer {
  val requestReceived: Counter = Counter
    .build("ubirch_niomon_receiver_http_requests_count", "Number of http request received.")
    .register()

  val responsesSent: Counter = Counter
    .build("ubirch_niomon_receiver_http_responses_count", "Number of http responses sent.")
    .labelNames("status")
    .register()
}
