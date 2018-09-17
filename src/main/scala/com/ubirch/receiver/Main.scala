package com.ubirch.receiver

import akka.http.scaladsl.settings.ServerSettings
import com.typesafe.config.ConfigFactory
import com.ubirch.receiver.http.HttpServer

object Main {


  def main(args: Array[String]) {
    new HttpServer().startServer("0.0.0.0", 8080, ServerSettings(ConfigFactory.load))
  }
}
