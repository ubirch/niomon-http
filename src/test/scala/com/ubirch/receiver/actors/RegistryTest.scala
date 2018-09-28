package com.ubirch.receiver.actors

import java.util.concurrent.TimeUnit

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import akka.util.Timeout
import org.scalatest.{FlatSpec, Matchers}

class RegistryTest extends FlatSpec with Matchers {
  private implicit val system: ActorSystem = ActorSystem("RegistryTest")
  private implicit val timeout: Timeout = Timeout(1, TimeUnit.SECONDS)

  private val handler = TestProbe().ref

  behavior of "Registry"

  it should "reply with a None to ResolveRequestHandler if id is unknown" in {
    val probe = TestProbe()
    val registry = system.actorOf(Props[Registry])

    //when
    probe.send(registry, ResolveRequestHandler("unknown"))


    //then
    probe.expectMsg(None)
  }

  it can "register and resolve handlers" in {
    val probe = TestProbe()
    val registry = system.actorOf(Props[Registry])

    //when
    registry ! RegisterRequestHandler(RequestHandlerReference("id", handler))
    probe.send(registry, ResolveRequestHandler("id"))


    //then
    probe.expectMsg(Some(RequestHandlerReference("id", handler)))
  }

  it should "unregister handlers and stops them afterwards" in {
    val probe = TestProbe()
    val watch = TestProbe()
    watch.watch(handler)
    val registry = system.actorOf(Props[Registry])

    //when
    registry ! RegisterRequestHandler(RequestHandlerReference("id", handler))
    registry ! UnregisterRequestHandler("id")
    probe.send(registry, ResolveRequestHandler("id"))


    //then
    probe.expectMsg(None)
    watch.expectTerminated(handler)
  }

}
