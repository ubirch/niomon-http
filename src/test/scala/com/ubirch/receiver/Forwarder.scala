package com.ubirch.receiver

import akka.actor.{Actor, ActorRef}

class Forwarder(target: ActorRef) extends Actor {
  def receive: PartialFunction[Any, Unit] = {
    case x ⇒ target ! x
  }
}
