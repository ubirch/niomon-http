package com.ubirch.receiver.http

import java.util.UUID

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.ubirch.receiver._

import scala.io.StdIn

class HttpServer {


  def serveHttp() {
    val route: Route =
      path("") {
        post {
          entity(as[String]) { input =>
            val published = publish(UUID.randomUUID().toString, input)
            onComplete(published) { output =>
              complete(output)
            }

          }
        } ~
          get {
            complete(HttpEntity("hello world"))
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

}