package com.ubirch.receiver.http

import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ubirch.receiver
import com.ubirch.receiver._


class HttpServer {

  private val port =receiver.conf.getInt("http.port")

  def serveHttp() {
    println(s"binding to port $port")
    val route: Route = {
      path("") {
        extractRequest {
          req =>
            entity(as[Array[Byte]]) {
              input =>
                val published = publish(RequestData(UUID.randomUUID().toString, input, getHeaders(req)))
                onComplete(published) {
                  output =>
                    complete(output)
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