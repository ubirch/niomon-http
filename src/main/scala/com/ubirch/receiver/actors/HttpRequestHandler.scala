package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver.kafka.KafkaPublisher

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

final case class RequestData(requestId: String, envelope: MessageEnvelope[Array[Byte]])

final case class ResponseData(requestId: String, envelope: MessageEnvelope[Array[Byte]])
