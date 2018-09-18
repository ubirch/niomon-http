package com.ubirch.receiver.http

import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ubirch.receiver._

import scala.io.StdIn

class HttpServer {


  def serveHttp() {
    val route: Route =
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
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    // ToDo BjB 17.09.18 : Graceful shutdown
    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => system.terminate())
  }

  private def getHeaders(req: HttpRequest) = {
    // ToDo BjB 18.09.18 : what else do we need here?
    val contentType = Map("Content-Type" -> req.entity.contentType.mediaType.toString())

    contentType
  }
}