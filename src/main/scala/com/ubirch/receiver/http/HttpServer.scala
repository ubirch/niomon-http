package com.ubirch.receiver.http

import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.server.{HttpApp, Route}

class HttpServer extends HttpApp {


  override def routes: Route =
    path("") {
      post {
        entity(as[String]) { input =>
          complete(HttpEntity("hello world"))
        }
      } ~
        get {
          complete(HttpEntity("hello world"))
        }
    }

}