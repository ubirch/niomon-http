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

    Thread.sleep(100) // scalastyle:off magic.number

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
