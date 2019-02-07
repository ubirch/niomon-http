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

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.ubirch.receiver.kafka.KafkaPublisher
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
  * Each actor of this type serves one specific HTTP request.
  * From each request the request data is published kafka.
  * When the responseData arrives from kafka it is send to the original requester
  */
class HttpRequestHandler(registry: ActorRef, requester: ActorRef, publisher: KafkaPublisher) extends Actor with ActorLogging {

  def receive: Receive = {
    case RequestData(k, e) =>
      log.debug(s"received input with requestId [$k]")
      publisher.send(key = k, e)
    case response: ResponseData =>
      log.debug(s"received response with requestId [${response.requestId}]")
      requester ! response
      registry ! UnregisterRequestHandler(response.requestId)
  }

  override def postStop(): Unit = {
    log.debug("stopped")
  }
}

final case class RequestData(requestId: String, record: (Array[Byte], Map[String, String]))

final case class ResponseData(requestId: String, record: ConsumerRecord[String, Array[Byte]])
