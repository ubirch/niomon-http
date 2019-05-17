/*
 * Copyright (c) 2019 ubirch GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ubirch.receiver.actors

import akka.actor.Status.Failure
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.TestProbe
import com.ubirch.receiver.kafka.{KafkaPublisher, PublisherException, PublisherSuccess}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

import scala.concurrent.Future

class HttpRequestHandlerTest extends FlatSpec with MockitoSugar with ArgumentMatchersSugar with BeforeAndAfterAll with Matchers {

  private implicit val system: ActorSystem = ActorSystem("HttpRequestHandlerTest")

  import system.dispatcher

  behavior of "HttpRequestHandler"

  it should "send request data to kafka" in {
    //given
    val kafkaPublisher = mock[KafkaPublisher]
    val requestHandler = system.actorOf(Props(classOf[HttpRequestHandler], mock[ActorRef], mock[ActorRef], kafkaPublisher))
    val requestData = RequestData("requestId", "value".getBytes, Map())

    // when
    requestHandler ! requestData

    Thread.sleep(100) // scalastyle:off magic.number

    // then
    verify(kafkaPublisher).send(eqTo(requestData.requestId), eqTo(requestData.payload), eqTo(requestData.headers))(eqTo(dispatcher))
  }

  it should "forward response data to returnTo actor" in {
    //given
    val returnTo = TestProbe()
    val requestHandler = system.actorOf(Props(classOf[HttpRequestHandler], TestProbe().ref, returnTo.ref, mock[KafkaPublisher]))
    val responseData = ResponseData("requestId", new ConsumerRecord("", 0, 0, "requestId", "value".getBytes))

    // when
    requestHandler ! responseData

    // then
    returnTo.expectMsg(responseData)
  }

  it should "send UnregisterRequestHandler with requestId to registry after handling response data" in {
    //given
    val registry = TestProbe()
    val requestHandler = system.actorOf(Props(classOf[HttpRequestHandler], registry.ref, TestProbe().ref, mock[KafkaPublisher]))
    val responseData = ResponseData("requestId", new ConsumerRecord("", 0, 0, "requestId", "value".getBytes))

    // when
    requestHandler ! responseData

    // then
    registry.expectMsg(UnregisterRequestHandler(responseData.requestId))
  }

  it should "handle publisher errors" in {
    //given
    val returnTo = TestProbe()
    val expectedException = new RuntimeException("a wild publisher exception!")
    val kafkaPublisher = mock[KafkaPublisher](new Answer[Future[PublisherSuccess]] {
      override def answer(invocation: InvocationOnMock): Future[PublisherSuccess] =
        Future.failed(PublisherException(expectedException, invocation.getArgument[String](0)))
    })
    val requestHandler = system.actorOf(Props(classOf[HttpRequestHandler], TestProbe().ref, returnTo.ref, kafkaPublisher))
    val request = RequestData("requestId", Array(0, 1, 2), Map())

    //when
    requestHandler ! request

    //then
    val f = returnTo.expectMsgClass(classOf[Failure])
    f.cause shouldBe a[PublisherException]
    f.cause.asInstanceOf[PublisherException].cause should equal(expectedException)
  }

  override protected def afterAll(): Unit = {
    val _ = system.terminate()
  }
}
