
package com.ubirch.receiver.actors

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

class ClusterListener(clusterAwareRegistry: ActorRef) extends Actor with ActorLogging {

  val cluster = Cluster(context.system)

  // subscribe to cluster changes, re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[MemberEvent], classOf[UnreachableMember])
  }
  override def postStop(): Unit = cluster.unsubscribe(self)

  def receive:Receive = {
    case MemberUp(member) ⇒
      log.info("Member is Up: {}", member.address)
      // wait vor pubsub to establish
      Thread.sleep(1000)
      clusterAwareRegistry ! NewMemberJoined
    case _: MemberEvent ⇒ // ignore
  }
}