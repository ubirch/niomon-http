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

import java.util.Base64

import akka.actor.Status.Failure
import akka.actor.{Actor, ActorRef, DiagnosticActorLogging}
import akka.event.Logging
import akka.event.Logging.MDC
import akka.pattern.pipe
import akka.serialization.Serialization
import com.ubirch.receiver.kafka.{KafkaPublisher, PublisherException, PublisherSuccess}
import org.apache.kafka.clients.producer.RecordMetadata

/**
  * Each actor of this type serves one specific HTTP request.
  * From each request the request data is published kafka.
  * When the responseData arrives from kafka it is send to the original requester
  */
class HttpRequestHandler(requester: ActorRef, publisher: KafkaPublisher) extends Actor with DiagnosticActorLogging {
  import context.dispatcher

  var startMillis = 0L

  override def mdc(currentMessage: Any): MDC = currentMessage match {
    case r: RequestData => Map("requestId" -> r.requestId) ++
      (if (log.isDebugEnabled) Map(
        "headers" -> r.headers.map(kv => s"${kv._1}=${kv._2}").mkString(","),
        "data" -> Base64.getEncoder.encodeToString(r.payload))
      else Nil)
    case r: ResponseData => Map("requestId" -> r.requestId)
    case f@Failure(PublisherException(cause@_, requestId)) =>
      Map("requestId" -> requestId, "failure" -> f.cause.getMessage)
    case PublisherSuccess(_, requestId: String) => Map("requestId" -> requestId)
    case _ => Logging.emptyMDC
  }

  def receive: Receive = {
    case RequestData(k, p, h) =>
      log.debug(s"received input with requestId [$k]")
      val selfPath = Serialization.serializedActorPath(self)
      publisher.send(k, p, h + ("http-request-handler-actor" -> selfPath)) pipeTo self
      startMillis = System.currentTimeMillis()
    case response: ResponseData =>
      log.debug(s"received response with requestId [${response.requestId}]")
      requester ! response
      logRequestResponseTime(response)
      context.stop(self)

    case f@Failure(PublisherException(cause, requestId)) =>
      log.error(cause, s"publisher failed for requestId [$requestId]")
      requester ! f
      context.stop(self)
    case PublisherSuccess(_: RecordMetadata, requestId: String) =>
      log.debug(s"request with requestId [$requestId] published successfully")
  }

  private def logRequestResponseTime(response: ResponseData): Unit = {
    val time = System.currentTimeMillis() - startMillis
    log.info(s"took $time ms to respond to [${response.requestId}]")
    if (time > 500 && time < 10000) {
      log.warning(s"processing took more than half a second for request with id [${response.requestId}]")
    }
    if (time >= 10000) {
      log.error(s"processing took more than 10 seconds for request with id [${response.requestId}], " +
        "client most likely timed out")
    }
  }
}

final case class RequestData(requestId: String, payload: Array[Byte], headers: Map[String, String])

final case class ResponseData(requestId: String, headers: Map[String, String], data: Array[Byte])
