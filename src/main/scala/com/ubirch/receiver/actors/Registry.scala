package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.collection.mutable

/**
  * Acts as registry for requestId -> HttpRequestHandler
  * Holds the actual references in a map.
  * Stops according actors when they get unregistered.
  */
class Registry extends Actor with ActorLogging {
  val registry: mutable.Map[String, ActorRef] = mutable.Map()

  override def receive: Receive = {
    case reg: RegisterRequestHandler =>
      registry.put(reg.requestHandlerReference.requestId, reg.requestHandlerReference.actorRef)
    case unreg: UnregisterRequestHandler =>
      registry.remove(unreg.requestId).foreach(context.stop)
    case resolve: ResolveRequestHandler =>
      registry.get(resolve.requestId) match {
        case Some(ref) =>
          sender() ! Some(RequestHandlerReference(resolve.requestId, ref))
        case None => sender() ! None
      }
  }

  }

case class RequestHandlerReference(requestId: String, actorRef: ActorRef)

case class RegisterRequestHandler(requestHandlerReference: RequestHandlerReference)

case class UnregisterRequestHandler(requestId: String)

case class ResolveRequestHandler(requestId: String)
