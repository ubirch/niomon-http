package com.ubirch.receiver

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.ubirch.receiver.kafka.KafkaPublisher

package object actors {

  type HttpRequestHandlerCreator = (ActorRefFactory, ActorRef, ActorRef, String) => ActorRef

  def requestHandlerCreator(kafkaPublisher:KafkaPublisher): HttpRequestHandlerCreator =
    (factory: ActorRefFactory,
     registry: ActorRef,
     respondTo: ActorRef,
     requestId: String) => factory.actorOf(Props(classOf[HttpRequestHandler], registry, respondTo, kafkaPublisher), requestId)
}
