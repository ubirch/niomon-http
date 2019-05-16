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
import com.ubirch.kafka.RichAnyConsumerRecord
import com.ubirch.receiver.actors.{RequestData, ResponseData}
import com.ubirch.receiver.http.HttpServer._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class HttpServer(port: Int, dispatcher: ActorRef)(implicit val system: ActorSystem) {

  val log: Logger = Logger[HttpServer]
  implicit val context: ExecutionContext = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(30, TimeUnit.SECONDS) // scalastyle:off magic.number

  def serveHttp() {
    val route: Route = {
      post {
        pathEndOrSingleSlash {
          extractRequest {
            req =>
              entity(as[Array[Byte]]) {
                input =>
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

  private def getHeaders(req: HttpRequest): Map[String, String] = {
    val headersToPreserve = req.headers.filter(h =>
      h.name() == "Content-Type" ||
      h.name() == "Authorization" ||
      h.name() == "X-XSRF-TOKEN" ||
      h.name() == "X-Cumulocity-BaseUrl" ||
      h.name() == "X-Cumulocity-Tenant" ||
      h.name() == "X-Niomon-Purge-Caches"
    ) ++ req.header[Cookie].flatMap { c => c.cookies.find(_.name == "authorization").map(Cookie(_)) }

    Map("Request-URI" -> req.uri.toString) ++ headersToPreserve.map(h => h.name -> h.value)
  }
}

object HttpServer {
  final class `X-XSRF-TOKEN`(token: String) extends ModeledCustomHeader[`X-XSRF-TOKEN`] {
    override def value(): String = token
    override def renderInRequests(): Boolean = true
    override def renderInResponses(): Boolean = true
    override def companion: ModeledCustomHeaderCompanion[`X-XSRF-TOKEN`] = `X-XSRF-TOKEN`
  }

  final class `X-Niomon-Purge-Caches`(inner: String) extends ModeledCustomHeader[`X-Niomon-Purge-Caches`] {
    override def companion: ModeledCustomHeaderCompanion[`X-Niomon-Purge-Caches`] = `X-Niomon-Purge-Caches`
    override def value(): String = inner
    override def renderInRequests(): Boolean = true
    override def renderInResponses(): Boolean = true
  }

  object `X-Niomon-Purge-Caches` extends ModeledCustomHeaderCompanion[`X-Niomon-Purge-Caches`] {
    override def name: String = "X-Niomon-Purge-Caches"
    override def parse(value: String): Try[`X-Niomon-Purge-Caches`] = Try(new `X-Niomon-Purge-Caches`(value))
  }

  object `X-XSRF-TOKEN` extends ModeledCustomHeaderCompanion[`X-XSRF-TOKEN`] {
    override def name: String = "X-XSRF-TOKEN"
    override def parse(value: String): Try[`X-XSRF-TOKEN`] = Try(new `X-XSRF-TOKEN`(value))
  }

  final class `X-Cumulocity-BaseUrl`(baseUrl: String) extends ModeledCustomHeader[`X-Cumulocity-BaseUrl`] {
    override def value(): String = baseUrl
    override def renderInRequests(): Boolean = true
    override def renderInResponses(): Boolean = true
    override def companion: ModeledCustomHeaderCompanion[`X-Cumulocity-BaseUrl`] = `X-Cumulocity-BaseUrl`
  }

  object `X-Cumulocity-BaseUrl` extends ModeledCustomHeaderCompanion[`X-Cumulocity-BaseUrl`] {
    override def name: String = "X-Cumulocity-BaseUrl"
    override def parse(value: String): Try[`X-Cumulocity-BaseUrl`] = Try(new `X-Cumulocity-BaseUrl`(value))
  }

  final class `X-Cumulocity-Tenant`(tenant: String) extends ModeledCustomHeader[`X-Cumulocity-Tenant`] {
    override def value(): String = tenant
    override def renderInRequests(): Boolean = true
    override def renderInResponses(): Boolean = true
    override def companion: ModeledCustomHeaderCompanion[`X-Cumulocity-Tenant`] = `X-Cumulocity-Tenant`
  }

  object `X-Cumulocity-Tenant` extends ModeledCustomHeaderCompanion[`X-Cumulocity-Tenant`] {
    override def name: String = "X-Cumulocity-Tenant"
    override def parse(value: String): Try[`X-Cumulocity-Tenant`] = Try(new `X-Cumulocity-Tenant`(value))
  }
}
