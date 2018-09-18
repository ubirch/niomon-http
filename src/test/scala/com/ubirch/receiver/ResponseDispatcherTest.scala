package com.ubirch.receiver

import akka.actor.{ActorSystem, Props}
import akka.testkit._
import com.ubirch.receiver.Actors.ResponseDispatcher
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}


class ResponseDispatcherTest extends TestKit(ActorSystem("RecordDispatcherTest")) with FlatSpecLike with Matchers with BeforeAndAfterAll {


  "RecordDispatcher" must "send to Receiver named by requestId" in {
    // given
    val receiver = TestProbe()
    system.actorOf(Props(classOf[Forwarder], receiver.ref), "requestId")

    val responseDispatcher = system.actorOf(Props(classOf[ResponseDispatcher]))

    // when
    responseDispatcher ! ResponseData("requestId", "value".getBytes, Map())

    // then
    receiver.expectMsgClass(classOf[ResponseData]).requestId should equal("requestId")
  }


  override def afterAll: Unit = {
    system.terminate()
  }

}
