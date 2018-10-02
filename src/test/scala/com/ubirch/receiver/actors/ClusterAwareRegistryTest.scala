package com.ubirch.receiver.actors

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.cluster.pubsub.DistributedPubSubMediator.{Publish, Subscribe}
import akka.testkit.TestProbe
import akka.util.Timeout
import com.ubirch.receiver.actors.ClusterAwareRegistry.REQUESTS_TOPIC
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

class ClusterAwareRegistryTest extends FlatSpec with MockitoSugar with Matchers {
  private implicit val system: ActorSystem = ActorSystem("ClusterAwareRegistry")
  private implicit val timeout: Timeout = Timeout(1, TimeUnit.SECONDS)

  private val handler = TestProbe().ref

  behavior of "ClusterAwareRegistry"

  it must "register itself to PublishSubscibe REQUESTS_TOPIC in cluster at startup" in {
    // given
    val clusterPubSub = TestProbe()

    //when
    val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], clusterPubSub.ref, TestProbe().ref))

    //then
    clusterPubSub.expectMsg(Subscribe(REQUESTS_TOPIC, clusterRegistry))
  }

  it should "delegate registering to registry and resolve handlers from there" in {
    val probe = TestProbe()
    val registry = system.actorOf(Props[Registry])
    val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], TestProbe().ref, registry))

    //when
    clusterRegistry ! RegisterRequestHandler(RequestHandlerReference("id", handler))
    probe.send(clusterRegistry, ResolveRequestHandler("id"))

    //then
    probe.expectMsg(Some(RequestHandlerReference("id", handler)))
  }

  it should "register handlers in registry and publish the fact to the cluster" in {
    val registry = TestProbe()
    val clusterPubSub = TestProbe()
    val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], clusterPubSub.ref, registry.ref))


    //when
    clusterRegistry ! RegisterRequestHandler(RequestHandlerReference("id", handler))


    //then
    registry.expectMsg(RegisterRequestHandler(RequestHandlerReference("id", handler)))
    clusterPubSub.receiveN(2) should contain(
      Publish(REQUESTS_TOPIC, RegisterRequestHandlerInTheCluster(RequestHandlerReference("id", handler)))
    )
  }

  it should "register handlers from the cluster in registry without republishing" in {
    val registry = TestProbe()
    val clusterPubSub = TestProbe()
    val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], clusterPubSub.ref, registry.ref))
    clusterPubSub.expectMsg(Subscribe(REQUESTS_TOPIC, clusterRegistry))

    //when
    clusterRegistry ! RegisterRequestHandlerInTheCluster(RequestHandlerReference("id", handler))


    //then
    registry.expectMsg(RegisterRequestHandler(RequestHandlerReference("id", handler)))
    clusterPubSub.expectNoMessage()
  }

  it should "publish all handler references from registry to the cluster if a new node joined" in {
    val clusterPubSub = TestProbe()
    val registry = system.actorOf(Props[Registry])
    val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], clusterPubSub.ref, registry))
    registry ! RegisterRequestHandler(RequestHandlerReference("id", handler))

    //when
    clusterRegistry ! NewMemberJoined

    //then

    clusterPubSub.receiveN(2) should contain(
      Publish(REQUESTS_TOPIC, RegisterAllRequestHandlersInTheCluster(List(RequestHandlerReference("id", handler))))
    )
  }

  it should "register all handler references from cluster to registry" in {
    val registry = TestProbe()
    val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], TestProbe().ref, registry.ref))

    //when
    clusterRegistry ! RegisterAllRequestHandlersInTheCluster(List(RequestHandlerReference("id", handler)))

    //then
    registry.expectMsg(RegisterAllRequestHandlers(List(RequestHandlerReference("id", handler))))
  }

  it should "unregister handlers from the cluster in registry without republishing" in {
    val clusterPubSub = TestProbe()
    val registry = TestProbe()
    val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], clusterPubSub.ref, registry.ref))
    clusterPubSub.expectMsg(Subscribe(REQUESTS_TOPIC, clusterRegistry))
    //when
    clusterRegistry ! UnregisterRequestHandlerInTheCluster("id")

    //then
    registry.expectMsg(UnregisterRequestHandler("id"))
    clusterPubSub.expectNoMessage()
  }

}
