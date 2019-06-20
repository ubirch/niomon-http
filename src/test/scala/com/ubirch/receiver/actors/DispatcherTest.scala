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

import akka.actor.{ActorSystem, Props}
import akka.testkit.TestProbe
import com.ubirch.receiver.kafka.KafkaPublisher
import org.mockito.{ArgumentMatchersSugar, MockitoSugar}
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}

class DispatcherTest extends FlatSpec with MockitoSugar with ArgumentMatchersSugar with Matchers with BeforeAndAfterAll {

  private implicit val system: ActorSystem = ActorSystem("DispatcherTest")


  behavior of "Dispatcher"


  it should "create a request handler for handling request data and register them in registry" in {
    val createdRequestHandler = TestProbe()
    val registry = TestProbe()
    val creator: HttpRequestHandlerCreator = (_, _, _, _) => createdRequestHandler.ref
    val dispatcher = system.actorOf(Props(classOf[Dispatcher], registry.ref, creator))
    val requestData = RequestData("someId", "value".getBytes, Map())

    // when
    dispatcher ! requestData

    //then
    registry.expectMsg(RegisterRequestHandler(RequestHandlerReference("someId", createdRequestHandler.ref)))
    createdRequestHandler.expectMsg(requestData)
  }


  it should "send response data to the requestHandler for the some requestId" in {
    //given
    val registry = system.actorOf(Props(classOf[Registry]))
    val dispatcher = system.actorOf(Props(classOf[Dispatcher], registry, requestHandlerCreator(mock[KafkaPublisher])))
    val responseData = ResponseData("someId", Map(), "value".getBytes)

    val someRequestHandler = TestProbe()
    registry ! RegisterRequestHandler(RequestHandlerReference("someId", someRequestHandler.ref))

    // when
    dispatcher ! responseData

    // then
    someRequestHandler.expectMsg(responseData)
  }

  override protected def afterAll(): Unit = {
    val _ = system.terminate()
  }
}
