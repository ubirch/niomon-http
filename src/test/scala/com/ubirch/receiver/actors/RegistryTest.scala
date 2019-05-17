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
import org.scalatest.{FlatSpec, Matchers}

class RegistryTest extends FlatSpec with Matchers {
  private implicit val system: ActorSystem = ActorSystem("RegistryTest")

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


  it can "resolve all handlers" in {
    val probe = TestProbe()
    val registry = system.actorOf(Props[Registry])

    //when
    registry ! RegisterRequestHandler(RequestHandlerReference("id1", handler))
    registry ! RegisterRequestHandler(RequestHandlerReference("id2", handler))
    registry ! RegisterRequestHandler(RequestHandlerReference("id3", handler))
    probe.send(registry, ResolveAllRequestHandlers)


    //then
    val someReferences = probe.expectMsgClass[AllRequestHandlerReferences](classOf[AllRequestHandlerReferences])
    someReferences.handerReferences should contain allOf(
      RequestHandlerReference("id1", handler),
      RequestHandlerReference("id2", handler),
      RequestHandlerReference("id3", handler)
    )
  }

  it can "register multiple handlers at once" in {
    val probe = TestProbe()
    val registry = system.actorOf(Props[Registry])

    //when
    registry ! RegisterAllRequestHandlers(
      List(RequestHandlerReference("id1", handler),
        RequestHandlerReference("id2", handler),
        RequestHandlerReference("id3", handler)))

    probe.send(registry, ResolveAllRequestHandlers)


    //then
    val someReferences = probe.expectMsgClass[AllRequestHandlerReferences](classOf[AllRequestHandlerReferences])
    someReferences.handerReferences should contain allOf(
      RequestHandlerReference("id1", handler),
      RequestHandlerReference("id2", handler),
      RequestHandlerReference("id3", handler)
    )
  }

  it can "register multiple handlers without loosing existing ones" in {
    val probe = TestProbe()
    val registry = system.actorOf(Props[Registry])

    //when
    registry ! RegisterRequestHandler(RequestHandlerReference("existing", handler))
    registry ! RegisterAllRequestHandlers(List(RequestHandlerReference("new", handler)))

    probe.send(registry, ResolveAllRequestHandlers)

    //then
    val someReferences = probe.expectMsgClass[AllRequestHandlerReferences](classOf[AllRequestHandlerReferences])
    someReferences.handerReferences should contain allOf(
      RequestHandlerReference("existing", handler),
      RequestHandlerReference("new", handler)
    )
  }


}
