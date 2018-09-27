package com.ubirch.receiver

import com.ubirch.receiver
import com.ubirch.receiver.http.HttpServer

object Main {

  def main(args: Array[String]) {
    new HttpServer(conf.getInt("http.port"), receiver.dispatcher).serveHttp()
  }
}
