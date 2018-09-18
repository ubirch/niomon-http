package com.ubirch.receiver

import akka.actor.{ActorSystem, Props}
import akka.testkit._
import com.ubirch.receiver.Actors.RecordDispatcher
import org.scalatest.{BeforeAndAfterAll, FlatSpecLike, Matchers}


class RecordDispatcherTest extends TestKit(ActorSystem("RecordDispatcherTest")) with FlatSpecLike with Matchers with BeforeAndAfterAll {


  "RecordDispatcher" must "send to Receiver named by requestId" in {
    // given
    val receiver = TestProbe()
    system.actorOf(Props(classOf[Forwarder], receiver.ref), "requestId")

    val recordDispatcher = system.actorOf(Props(classOf[RecordDispatcher]))

    // when
    recordDispatcher ! ResponseData("requestId", "value".getBytes, Map())

    // then
    receiver.expectMsgClass(classOf[ResponseData])
  }


  override def afterAll: Unit = {
    system.terminate()
  }

}
