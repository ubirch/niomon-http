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

import akka.actor.{Actor, ActorLogging, ActorRef}

import scala.collection.mutable

/**
  * Acts as registry for requestId -> HttpRequestHandler
  * Holds the actual references in a map.
  * Stops according actors when they get unregistered.
  */
class Registry extends Actor with ActorLogging {
  val registry: mutable.Map[String, ActorRef] = mutable.Map()

  override def receive: Receive = {
    case reg: RegisterRequestHandler =>
      log.debug(s"RegisterRequestHandler ${reg.requestHandlerReference.requestId}")
      registry.put(reg.requestHandlerReference.requestId, reg.requestHandlerReference.actorRef)
    case unreg: UnregisterRequestHandler =>
      log.debug(s"UnregisterRequestHandler ${unreg.requestId}")
      registry.remove(unreg.requestId).foreach(context.stop)
    case resolve: ResolveRequestHandler =>
      registry.get(resolve.requestId) match {
        case Some(ref) =>
          log.debug(s"resolved handler ${resolve.requestId}")
          sender() ! Some(RequestHandlerReference(resolve.requestId, ref))
        case None => sender() ! None
      }
    case ResolveAllRequestHandlers =>
      val references = registry.map(x => RequestHandlerReference(x._1, x._2)).toList
      log.debug(s"resolved all  ${references.mkString}")
      sender() ! AllRequestHandlerReferences(references)
    case RegisterAllRequestHandlers(references) =>
      log.debug(s"registering all of ${references.mkString}")
      references.foreach(ref => registry.put(ref.requestId, ref.actorRef))
  }
}

case class RequestHandlerReference(requestId: String, actorRef: ActorRef)

case class RegisterRequestHandler(requestHandlerReference: RequestHandlerReference)

case class RegisterAllRequestHandlers(handerReferences: List[RequestHandlerReference])

case class UnregisterRequestHandler(requestId: String)

case class ResolveRequestHandler(requestId: String)

case class ResolveAllRequestHandlers()

case class AllRequestHandlerReferences(handerReferences: List[RequestHandlerReference])
