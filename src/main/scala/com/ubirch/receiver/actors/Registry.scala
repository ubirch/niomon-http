package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.collection.mutable

class Registry extends Actor with ActorLogging {
  val registry: mutable.Map[String, ActorRef] = mutable.Map()

  override def receive: Receive = {
    case reg: RegisterRequestHandler =>
      register(reg)
    case unreg: UnregisterRequestHandler =>
      unregister(unreg)
    case resolve: ResolveRequestHandler =>
      registry.get(resolve.requestId) match {
        case Some(ref) =>
          sender() ! Some(RequestHandlerReference(resolve.requestId, ref))
        case None => sender() ! None
      }
  }

  protected def unregister(unreg: UnregisterRequestHandler): Unit = {
    registry.remove(unreg.requestId).foreach(context.stop)
  }

  protected def register(reg: RegisterRequestHandler): Unit = {
    registry.put(reg.requestHandlerReference.requestId, reg.requestHandlerReference.actorRef)
  }
}

case class RequestHandlerReference(requestId: String, actorRef: ActorRef)

case class RegisterRequestHandler(requestHandlerReference: RequestHandlerReference)

case class UnregisterRequestHandler(requestId: String)

case class ResolveRequestHandler(requestId: String)
