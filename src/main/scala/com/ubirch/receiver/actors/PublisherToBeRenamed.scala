package com.ubirch.receiver.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.pattern.ask
import akka.util.Timeout
import com.ubirch.receiver.publisher

class PublisherToBeRenamed(dispatcher: ActorRef, pubsubMediator: ActorRef) extends Actor {

  override def receive: Receive = {
    case requestData: RequestData =>
      implicit val timeout= Timeout(10, TimeUnit.SECONDS)

      val rd = context.actorOf(Props(classOf[RequestActor], dispatcher, publisher), requestData.requestId)
      pubsubMediator ! Publish("requests", CreateRequestRef(requestData.requestId, rd))

      rd ? requestData
  }
}

case class ResponseRef(responseData: ResponseData, returnTo: ActorRef)