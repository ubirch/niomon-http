package com.ubirch.receiver.actors

import java.net.InetAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.ClusterEvent.CurrentClusterState
import com.typesafe.config.Config
import io.prometheus.client.Gauge

class ClusterStateMonitor(config: Config) extends Actor with ActorLogging {
  import ClusterStateMonitor._

  private val requiredContactPoints = config.getInt("akka.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr")

  log.info("Starting Cluster State Monitor - required-contact-point-nr={}", requiredContactPoints)

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

      val localhost: InetAddress = InetAddress.getLocalHost
      val localIpAddress: String = localhost.getHostAddress
      val isLeader = state.leader.filter(x => x.toString.contains(localIpAddress)).map(_ => 1).getOrElse(0)

      isLeaderGauge.set(isLeader.toDouble)
      leaderGauge.set(leaderSize.toDouble)
      membersGauge.set(membersSize.toDouble)
      unreachableGauge.set(unreachableSize.toDouble)
      configuredRequiredContactPoints.set(requiredContactPoints.toDouble)

  }

}

object ClusterStateMonitor {

  val leaderGauge: Gauge = Gauge
    .build("akka_cluster_leader", "Akka Cluster Leader")
    .register()

  val isLeaderGauge: Gauge = Gauge
    .build("akka_cluster_is_leader", "Akka Cluster Is Leader")
    .register()

  val membersGauge: Gauge = Gauge
    .build("akka_cluster_members", "Akka Cluster Members")
    .register()

  val unreachableGauge: Gauge = Gauge
    .build("akka_cluster_unreachable", "Akka Cluster Unreachable Members")
    .register()

  val configuredRequiredContactPoints: Gauge = Gauge
    .build("akka_cluster_configured_req_contact_pts", "Akka Cluster Configured Required Contact Points")
    .register()

  def props(config: Config): Props = Props(new ClusterStateMonitor(config))

}
