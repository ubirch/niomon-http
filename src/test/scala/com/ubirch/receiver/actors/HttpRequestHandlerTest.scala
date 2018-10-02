package com.ubirch.receiver.actors

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver.kafka.KafkaPublisher
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

class HttpRequestHandlerTest extends FlatSpec with MockitoSugar with ArgumentMatchersSugar with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("HttpRequestHandlerTest")

  behavior of "HttpRequestHandler"

  it should "send request data to kafka" in {
    //given
    val kafkaPublisher = mock[KafkaPublisher]
    val requestHandler = system.actorOf(Props(classOf[HttpRequestHandler], mock[ActorRef], mock[ActorRef], kafkaPublisher))
    val requestData = RequestData("requestId", MessageEnvelope("value".getBytes, Map()))

    // when
    requestHandler ! requestData

    // then
    verify(kafkaPublisher).send(eqTo(requestData.requestId), eqTo(requestData.envelope))
  }

  it should "forward response data to returnTo actor" in {
    //given
    val returnTo = TestProbe()
    val requestHandler = system.actorOf(Props(classOf[HttpRequestHandler], TestProbe().ref, returnTo.ref, mock[KafkaPublisher]))
    val responseData = ResponseData("requestId", MessageEnvelope("value".getBytes, Map()))

    // when
    requestHandler ! responseData

    // then
    returnTo.expectMsg(responseData)
  }

  it should "send UnregisterRequestHandler with requestId to registry after handling response data" in {
    //given
    val registry = TestProbe()
    val requestHandler = system.actorOf(Props(classOf[HttpRequestHandler], registry.ref, TestProbe().ref, mock[KafkaPublisher]))
    val responseData = ResponseData("requestId", MessageEnvelope("value".getBytes, Map()))

    // when
    requestHandler ! responseData

    // then
    registry.expectMsg(UnregisterRequestHandler(responseData.requestId))
  }

  override protected def afterAll(): Unit = system.terminate()
}
