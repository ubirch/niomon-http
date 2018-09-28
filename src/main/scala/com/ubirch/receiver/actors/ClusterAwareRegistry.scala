package com.ubirch.receiver.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.pubsub.DistributedPubSubMediator
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.pattern.ask
import akka.util.Timeout
import com.ubirch.receiver.actors.ClusterAwareRegistry.REQUESTS_TOPIC

import scala.util.Success


object ClusterAwareRegistry {
  val REQUESTS_TOPIC: String = "requests"
}

/**
  * Acts as Registry for requestId -> HttpRequestHandler
  * Delegates to Registry and syncs with other cluster nodes via cluster PubSub.
  */
// ToDo BjB 28.09.18 : WIP This guy must populate the empty Regitry on startup from other nodes.
class ClusterAwareRegistry(clusterPubSub:ActorRef, registry:ActorRef) extends Actor with ActorLogging {
  implicit val timeout: Timeout = Timeout(100, TimeUnit.MILLISECONDS)
  import context._


  override def preStart(): Unit = {
    clusterPubSub ! DistributedPubSubMediator.Subscribe(REQUESTS_TOPIC, self)
  }

  override def receive: Receive = {
        case reg: RegisterRequestHandler =>
          log.debug(s"RegisterRequestHandler ${reg.requestHandlerReference.requestId}")
          registry ! reg
          if (sender()!=self) {
            clusterPubSub ! Publish(REQUESTS_TOPIC, reg)
          }
        case unreg: UnregisterRequestHandler =>
          log.debug(s"UnregisterRequestHandler ${unreg.requestId}")
          registry ! unreg
          if (sender()!=self) {
            clusterPubSub ! Publish(REQUESTS_TOPIC, unreg)
          }
        case resolve: ResolveRequestHandler =>
          val sendTo= sender()
          (registry ? resolve) onComplete {
            case Success(Some(reqHandler: RequestHandlerReference)) => sendTo ! Some(reqHandler)
            case _ => sendTo ! None
          }
      }
}
