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
class ClusterAwareRegistry(clusterPubSub: ActorRef, registry: ActorRef) extends Actor with ActorLogging {
  implicit val timeout: Timeout = Timeout(100, TimeUnit.MILLISECONDS)

  import context._


  override def preStart(): Unit = {
    clusterPubSub ! DistributedPubSubMediator.Subscribe(REQUESTS_TOPIC, self)
  }

  override def receive: Receive = {
    case reg: RegisterRequestHandler =>
      log.debug(s"RegisterRequestHandler ${reg.requestHandlerReference.requestId}")
      registry ! reg
      clusterPubSub ! Publish(REQUESTS_TOPIC, RegisterRequestHandlerInTheCluster(reg.requestHandlerReference))

    case unreg: UnregisterRequestHandler =>
      log.debug(s"UnregisterRequestHandler ${unreg.requestId}")
      registry ! unreg
      clusterPubSub ! Publish(REQUESTS_TOPIC, UnregisterRequestHandlerInTheCluster(unreg.requestId))

    case reg: RegisterRequestHandlerInTheCluster =>
      if (sender() != self) {
        log.debug(s"RegisterRequestHandlerCluster ${reg.requestHandlerReference.requestId}")
        registry ! RegisterRequestHandler(reg.requestHandlerReference)
      }

    case regAll: RegisterAllRequestHandlersInTheCluster =>
      log.debug(s"RegisterAllRequestHandlersInTheCluster ${regAll.handerReferences.mkString}")
      registry ! RegisterAllRequestHandlers(regAll.handerReferences)

    case unreg: UnregisterRequestHandlerInTheCluster =>
      if (sender() != self) {
        log.debug(s"UnregisterRequestHandlerCluster ${unreg.requestId}")
        registry ! UnregisterRequestHandler(unreg.requestId)
      }

    case resolve: ResolveRequestHandler =>
      val sendTo = sender()
      (registry ? resolve) onComplete {
        case Success(Some(reqHandler: RequestHandlerReference)) =>
          log.debug(s"resolved handler ${reqHandler.requestId}")
          sendTo ! Some(reqHandler)
        case _ => sendTo ! None
      }
    case NewMemberJoined ⇒
      log.debug("cluster member joined. Publishing all handler references")
      (registry ? ResolveAllRequestHandlers) onComplete {
        case Success(references: List[RequestHandlerReference]) =>
          log.debug(s"publishing  ${references.mkString}")
          clusterPubSub ! Publish(REQUESTS_TOPIC, RegisterAllRequestHandlersInTheCluster(references))
        case _ => log.error("could not resolve all handler references")
      }
  }
}

case class RegisterRequestHandlerInTheCluster(requestHandlerReference: RequestHandlerReference)

case class UnregisterRequestHandlerInTheCluster(requestId: String)

case class RegisterAllRequestHandlersInTheCluster(handerReferences: List[RequestHandlerReference])

case class NewMemberJoined()