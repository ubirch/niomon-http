/*
 * Copyright (c) 2019 ubirch GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ubirch.receiver

import skinny.http.{HTTP, Request}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext}

/**
  * For simple local load testing.
  * 1. Startup local kafka via `docker-compose up`
  * 2. Startup Main class with environment vars:
  * KAFKA_URL=localhost:9092
  * KAFKA_TOPIC_INCOMING_REQUESTS=incoming
  * KAFKA_TOPIC_OUTGOING_REQUESTS=incoming
  * 3. start ManualLoadTest
  */
object ManualLoadTest {

  // scalastyle:off regex
  def main(args: Array[String]) {

    val numberOfRequests = 1000

    val start = System.currentTimeMillis()

    val result = Range(0, numberOfRequests)
      .map(i => s"""{"value":"${i.toString}"}""")
      .map(data => {

        val request = Request("http://localhost:8080/")
          .body(data.getBytes(), "application/json")
          .userAgent("test")

        val response = HTTP.asyncPost(request)(ExecutionContext.global)

        (response, data)
      })
      .map(f => ResponseAndInput(Await.result(f._1, Duration(20, "seconds")), f._2)) // scalastyle:off magic.number

    val elapsedMs: Double = System.currentTimeMillis() - start
    val msPerReq: Double = elapsedMs / numberOfRequests

    println(s"processing $numberOfRequests requests took ${elapsedMs}ms which is about ${msPerReq}ms/req or ${1000 / msPerReq}req/s")

    result.foreach {
      r => {
        assert(r.response.status == 201)
        assert(r.response.textBody.contains(r.input.toString))
      }
    }

    println("all requests where processed correctly")
  }
}
