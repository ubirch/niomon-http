package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ClusterEvent.CurrentClusterState
import io.prometheus.client.Gauge

class ClusterStateMonitor extends Actor with ActorLogging {
  import ClusterStateMonitor._

  override def receive: Receive = {
    case state: CurrentClusterState =>

      val leaderSize = state.leader.toList.size
      val membersSize = state.members.size
      val unreachableSize = state.unreachable.size

      log.info("cluster_leader={} cluster_members_size={} cluster_members={} cluster_unreachable_members={}",
        state.leader.map(_.toString).getOrElse("None"),
        membersSize,
        state.members.map(_.toString()).toList,
        unreachableSize
      )

      leaderGauge.set(leaderSize.toDouble)
      membersGauge.set(membersSize.toDouble)
      unreachableGauge.set(unreachableSize.toDouble)

  }

}

object ClusterStateMonitor {

  val leaderGauge: Gauge = Gauge
    .build("akka_cluster_leader", "Akka Cluster Leader")
    .register()

  val membersGauge: Gauge = Gauge
    .build("akka_cluster_members", "Akka Cluster Members")
    .register()

  val unreachableGauge: Gauge = Gauge
    .build("akka_cluster_unreachable", "Akka Cluster Unreachable Members")
    .register()

  val props = Props[ClusterStateMonitor]
}
