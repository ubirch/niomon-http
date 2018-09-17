package com.ubirch.receiver

import com.ubirch.receiver.http.HttpServer

object Main {

  def main(args: Array[String]) {
    new HttpServer().serveHttp()
  }
}
