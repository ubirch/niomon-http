package com.ubirch.receiver.http

import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver
import com.ubirch.receiver._

import scala.util.Success


class HttpServer {

  private val port = receiver.conf.getInt("http.port")

  def serveHttp() {
    val route: Route = {
      path("") {
        extractRequest {
          req =>
            entity(as[Array[Byte]]) {
              input =>
                val requestId = UUID.randomUUID().toString
                val responseData = publish(RequestData(requestId, MessageEnvelope(input, getHeaders(req))))
                onComplete(responseData) {
                  case Success(result) =>
                    // ToDo BjB 21.09.18 : Revise Headers
                    val contentType = determineContentType(result.envelope.headers)
                    complete(HttpResponse(status=StatusCodes.Created,entity= HttpEntity(result.envelope.payload).withContentType(contentType)))
                  case _ =>
                    complete(HttpResponse(StatusCodes.InternalServerError))
                }
            }
        }
      } ~
      path("status") {
        complete("up")
      }
    }

    Http().bindAndHandle(route, "0.0.0.0", port)
    system.log.info(s"Server started at localhost:$port")
    // ToDo BjB 17.09.18 : Graceful shutdown

  }

  private def determineContentType(headers: Map[String, String]) = {
    ContentType.parse(headers.getOrElse(CONTENT_TYPE, "")) match {
      case Left(x) => ContentTypes.`application/octet-stream`
      case Right(x) => x
    }
  }

  private val CONTENT_TYPE = "Content-Type"
  private def getHeaders(req: HttpRequest): Map[String, String] = {
    Map(CONTENT_TYPE -> req.entity.contentType.mediaType.toString(),
        "Request-URI" -> req.getUri().toString)
  }
}