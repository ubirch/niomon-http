package com.ubirch.receiver.actors

import java.net.InetAddress

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, LeaderChanged, MemberJoined, MemberUp}
import com.typesafe.config.Config
import io.prometheus.client.Gauge

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps

class ClusterStateMonitor(cluster: Cluster, config: Config) extends Actor with ActorLogging {
  import ClusterStateMonitor._

  private val requiredContactPoints = config.getInt("akka.management.cluster.bootstrap.contact-point-discovery.required-contact-point-nr")

  log.info("Starting Cluster State Monitor - required-contact-point-nr={}", requiredContactPoints)

  override def preStart(): Unit = {
    implicit val ec: ExecutionContext = context.dispatcher

    context.system.scheduler.schedule(60 seconds, 60 seconds){
      cluster.sendCurrentClusterState(self)
    }
    cluster.subscribe(self, classOf[LeaderChanged], classOf[MemberUp], classOf[MemberJoined])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

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

    case LeaderChanged(leader) =>
      log.info("leader_changed_detected={}", leader.map(_.toString).getOrElse("Not found"))

    case MemberUp(member) =>
      log.info("member_up_detected={}", member.toString())

    case MemberJoined(member) =>
      log.info("member_joined_detected={}", member.toString())

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

  def props(cluster: Cluster, config: Config): Props = Props(new ClusterStateMonitor(cluster, config))

}
