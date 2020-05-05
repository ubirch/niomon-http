package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ClusterEvent.CurrentClusterState

class ClusterStateMonitor extends Actor with ActorLogging {

  override def receive: Receive = {
    case state: CurrentClusterState =>
      log.info("Received current cluster state: " + state.toString())
  }

}

object ClusterStateMonitor {
  val props = Props[ClusterStateMonitor]
}
