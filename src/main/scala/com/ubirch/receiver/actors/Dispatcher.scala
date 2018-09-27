package com.ubirch.receiver.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.util.Timeout
import com.ubirch.receiver.publisher

import scala.collection.mutable

class Dispatcher(clusterMediator: ActorRef) extends Actor with ActorLogging {
  val registry: mutable.Map[String, ActorRef] = mutable.Map()

  override def preStart(): Unit = {
    clusterMediator ! DistributedPubSubMediator.Subscribe("requests", self)
  }

  override def receive: Receive = {
    case cr: CreateRequestRef =>
      log.debug(s"received CreateRequestRef with requestId [${cr.requestId}] for actor [${cr.actorRef.toString()}]")
      registry.put(cr.requestId, cr.actorRef)

    case dr: DeleteRequestRef =>
      log.debug(s"received DeleteRequestRef with requestId [${dr.requestId}]")
      registry.remove(dr.requestId).foreach(context.stop)

    case req: RequestData =>
      log.debug(s"received RequestData with requestId [${req.requestId}]")
      implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)
      val reqHandler = context.actorOf(Props(classOf[HttpRequestHandler], self, sender(), publisher), req.requestId)
      clusterMediator ! Publish("requests", CreateRequestRef(req.requestId, reqHandler))
      reqHandler ! req

    case resp: ResponseData =>
      log.debug(s"received ResponseData with requestId [${resp.requestId}]")
      registry.get(resp.requestId) match {
        case Some(reqHandler) =>
          log.debug(s"forwarding response with requestId [${resp.requestId}] to actor [${reqHandler.toString()}]")
          reqHandler ! resp
        case None => log.error(s"could not find actor for request ${resp.requestId}")
      }
  }
}

case class CreateRequestRef(requestId: String, actorRef: ActorRef)

case class DeleteRequestRef(requestId: String)


