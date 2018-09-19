package com.ubirch.receiver


import java.util.concurrent.{LinkedBlockingQueue, ThreadPoolExecutor, TimeUnit}

import org.junit.jupiter.api.Disabled
import skinny.http.{HTTP, Request}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

class SimpleLoadTest extends org.scalatest.FunSuite {

  // ToDo BjB 17.09.18 : automated start of docker compose...
  ignore("a thousand requests...") {

    implicit val ec: HTTP.EC = ExecutionContext.fromExecutor(new ThreadPoolExecutor(2, 2, 12, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable](100)))

    val result = Range(1, 1000)
      .map(rnd => {
        val data =
          s"""{
             |  "value":"${rnd.toString}"
             |}
        """.stripMargin

        val request = Request("http://localhost:8080/")
          .body(data.getBytes(), "application/json")
          .userAgent("test").connectTimeoutMillis(5000).readTimeoutMillis(3000)

        val response = HTTP.asyncPost(request)

        (response, rnd)
      })
      .map(f => (Await.result(f._1, Duration(20, "seconds")), f._2))

    // ToDo BjB 17.09.18 : Print some meaningful output.

    result.foreach {
      r => {
        assert(r._1.status == 200)
        assert(r._1.textBody.contains(r._2.toString))
      }
    }
  }
}