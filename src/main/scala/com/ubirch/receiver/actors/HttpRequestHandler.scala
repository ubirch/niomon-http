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

package com.ubirch.receiver.actors

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.pipe
import com.ubirch.receiver.kafka.{KafkaPublisher, PublisherException, PublisherSuccess}
import org.apache.kafka.clients.producer.RecordMetadata

/**
  * Each actor of this type serves one specific HTTP request.
  * From each request the request data is published kafka.
  * When the responseData arrives from kafka it is send to the original requester
  */
class HttpRequestHandler(registry: ActorRef, requester: ActorRef, publisher: KafkaPublisher) extends Actor with ActorLogging {
  import context.dispatcher

  var startMillis = 0L

  def receive: Receive = {
    case RequestData(k, p, h) =>
      log.debug(s"received input with requestId [$k]")
      publisher.send(k, p, h) pipeTo self
      startMillis = System.currentTimeMillis()
    case response: ResponseData =>
      log.debug(s"received response with requestId [${response.requestId}]")
      requester ! response
      logRequestResponseTime(response)
      registry ! UnregisterRequestHandler(response.requestId)

    case f@Failure(PublisherException(cause, requestId)) =>
      log.error(cause, s"publisher failed for requestId [$requestId]")
      requester ! f
      registry ! UnregisterRequestHandler(requestId)
    case PublisherSuccess(_: RecordMetadata, requestId: String) =>
      log.debug(s"request with requestId [$requestId] published successfully")
  }

  private def logRequestResponseTime(response: ResponseData): Unit = {
    val time = System.currentTimeMillis() - startMillis
    log.info(s"Took $time ms to respond to [${response.requestId}]")
    if (time > 500 && time < 10000) {
      log.warning(s"Processing took more than half a second for request with id [${response.requestId}]!")
    }
    if (time >= 10000) {
      log.error(s"Processing took more than 10 seconds for request with id [${response.requestId}]. " +
        "Requesting client most likely timed out!")
    }
  }

  override def postStop(): Unit = {
    log.debug("stopped")
  }
}

final case class RequestData(requestId: String, payload: Array[Byte], headers: Map[String, String])

final case class ResponseData(requestId: String, headers: Map[String, String], data: Array[Byte])
