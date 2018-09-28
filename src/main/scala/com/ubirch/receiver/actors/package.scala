package com.ubirch.receiver

import akka.actor.{ActorRef, ActorRefFactory, Props}
import com.ubirch.receiver.kafka.KafkaPublisher

package object actors {

  type HttpRequestHandlerCreator = (ActorRefFactory, ActorRef, ActorRef, String, KafkaPublisher) => ActorRef

  val requestHandlerCreator: HttpRequestHandlerCreator =
    (factory: ActorRefFactory,
     registry: ActorRef,
     respondTo: ActorRef,
     requestId: String,
     kafkaPublisher: KafkaPublisher) => factory.actorOf(Props(classOf[HttpRequestHandler], registry, respondTo, kafkaPublisher), requestId)
}
