package com.ubirch.receiver.actors

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import com.ubirch.kafkasupport.MessageEnvelope
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class DispatcherTest extends FlatSpec with MockitoSugar with ArgumentMatchersSugar with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("DispatcherTest")


  behavior of "Dispatcher"


  it should "create a request handler for handling request data and register them in registry" in {
    val createdRequestHandler = TestProbe()
    val registry = TestProbe()
    val creator: HttpRequestHandlerCreator = (_, _, _, _, _) => createdRequestHandler.ref
    val dispatcher = system.actorOf(Props(classOf[Dispatcher], registry.ref, creator))
    val requestData = RequestData("someId", MessageEnvelope("value".getBytes, Map()))

    // when
    dispatcher ! requestData

    //then
    registry.expectMsg(RegisterRequestHandler(RequestHandlerReference("someId", createdRequestHandler.ref)))
    createdRequestHandler.expectMsg(requestData)
  }


  it should "send response data to the requestHandler for the some requestId" in {
    //given
    val registry = system.actorOf(Props(classOf[Registry]))
    val dispatcher = system.actorOf(Props(classOf[Dispatcher], registry, requestHandlerCreator))
    val responseData = ResponseData("someId", MessageEnvelope("value".getBytes, Map()))

    val someRequestHandler = TestProbe()
    registry ! RegisterRequestHandler(RequestHandlerReference("someId", someRequestHandler.ref))

    // when
    dispatcher ! responseData

    // then
    someRequestHandler.expectMsg(responseData)
  }

  override protected def afterAll(): Unit = system.terminate()
}
