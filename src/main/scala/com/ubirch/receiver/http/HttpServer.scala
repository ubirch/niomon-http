package com.ubirch.receiver.http

import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver
import com.ubirch.receiver._


class HttpServer {

  private val port = receiver.conf.getInt("http.port")

  def serveHttp() {
    println(s"binding to port $port")
    val route: Route = {
      path("") {
        extractRequest {
          req =>
            entity(as[Array[Byte]]) {
              input =>
                val responseData = publish(RequestData(UUID.randomUUID().toString, MessageEnvelope(input, getHeaders(req))))
                onComplete(responseData) {
                  output =>
                    // ToDo BjB 21.09.18 : Set response headers accordingly
                    complete(output.map(_.envelope.payload))
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

  private def getHeaders(req: HttpRequest): Map[String, String] = {
    Map("Content-Type" -> req.entity.contentType.mediaType.toString(),
        "Request-URI" -> req.getUri().toString)
  }
}