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
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import com.ubirch.receiver.actors.{RequestData, ResponseData}
import com.ubirch.kafka.RichAnyConsumerRecord

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class HttpServer(port: Int, dispatcher: ActorRef)(implicit val system: ActorSystem) {

  val log: Logger = Logger[HttpServer]
  implicit val context: ExecutionContext = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS) // scalastyle:off magic.number
  private val CONTENT_TYPE = "Content-Type"

  def serveHttp() {
    val route: Route = {
      path("") {
        extractRequest {
          req =>
            entity(as[Array[Byte]]) {
              input =>
                val requestId = UUID.randomUUID().toString
                val responseData = dispatcher ? RequestData(requestId, (input, getHeaders(req)))
                onComplete(responseData) {
                  case Success(res) =>
                    val result = res.asInstanceOf[ResponseData]
                    // ToDo BjB 21.09.18 : Revise Headers
                    val headers = result.record.headersScala
                    val contentType = determineContentType(headers)
                    val status = headers.get("http-status-code").map(_.toInt: StatusCode).getOrElse(StatusCodes.OK)
                    complete(HttpResponse(status = status, entity = HttpEntity(contentType, result.record.value())))
                  case Failure(e) =>
                    log.debug("dispatcher failure", e)
                    complete(StatusCodes.InternalServerError, e.getMessage)
                }
            }
        }
      } ~
        path("status") {
          complete("up")
        }
    }

    Http().bindAndHandle(route, "0.0.0.0", port) onComplete {
      case Success(v) => log.info(s"http server started: ${v.localAddress}")
      case Failure(e) => log.error("http server start failed", e)
    }
    // ToDo BjB 17.09.18 : Graceful shutdown

  }

  private def determineContentType(headers: Map[String, String]) = {
    ContentType.parse(headers.getOrElse(CONTENT_TYPE, "")) match {
      case Left(x) => ContentTypes.`application/octet-stream`
      case Right(x) => x
    }
  }

  private def getHeaders(req: HttpRequest): Map[String, String] = {
    Map(
      CONTENT_TYPE -> req.entity.contentType.mediaType.toString(),
      "Request-URI" -> req.getUri().toString,
    ) ++ req.header[Authorization].map(h => h.name -> h.value)
  }
}
