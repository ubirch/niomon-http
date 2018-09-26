package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver.kafka.KafkaPublisher


class RequestActor(dispatcher: ActorRef, publisher: KafkaPublisher) extends Actor with ActorLogging {

  import context._

  def receive: PartialFunction[Any, Unit] = {
    case RequestData(k, e) =>
      log.debug(s"received input with requestId [${k}]")
      publisher.send(key = k, e)
      become(outgoing(sender()))
  }

  private def outgoing(returnTo: ActorRef): Receive = {
    case response: ResponseData =>
      log.debug(s"received response with requestId [${response.requestId}]")
      returnTo ! response
      dispatcher ! DeleteRequestRef(response.requestId)
      context.stop(self)
  }
}

final case class RequestData(requestId: String, envelope: MessageEnvelope[Array[Byte]])

final case class ResponseData(requestId: String, envelope: MessageEnvelope[Array[Byte]])
