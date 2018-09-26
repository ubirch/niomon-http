package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.pubsub.DistributedPubSubMediator
import com.ubirch.receiver

import scala.collection.mutable

class Dispatcher extends Actor with ActorLogging {
  val registry: mutable.Map[String, ActorRef] = mutable.Map()

  override def preStart(): Unit = {
    receiver.pubsub.mediator ! DistributedPubSubMediator.Subscribe("requests", self)
  }

  override def receive: Receive = {
    case cr: CreateRequestRef => {
      log.debug(s"received CreateRequestRef with requestId [${cr.requestId}] for actor [${cr.actorRef.toString()}]")
      registry.put(cr.requestId, cr.actorRef)
    }
    case dr: DeleteRequestRef => {
      log.debug(s"received DeleteRequestRef with requestId [${dr.requestId}]")
      registry.remove(dr.requestId)
    }
    case resp: ResponseData =>
      registry.get(resp.requestId) match {
        case Some(ref) =>
          log.debug(s"forwarding response with requestId [${resp.requestId}] to actor [${ref.toString()}]")
          ref ! resp
        case None => log.error(s"could not find actor for request ${resp.requestId}")
      }
  }
}

case class CreateRequestRef(requestId: String, actorRef: ActorRef)

case class DeleteRequestRef(requestId: String)


