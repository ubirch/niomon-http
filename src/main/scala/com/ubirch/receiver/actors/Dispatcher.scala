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

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.pattern.ask
import akka.util.Timeout

import scala.util.{Failure, Success}

/**
  * Creates a HttpRequestHandler for each incoming RequestData from HTTP.
  * Routes the responses from kafka to the formerly created HttpRequestHandler
  */
class Dispatcher(registry: ActorRef, handlerCreator: HttpRequestHandlerCreator) extends Actor with ActorLogging {
  implicit val timeout: Timeout = Timeout(100, TimeUnit.MILLISECONDS) // scalastyle:off magic.number

  import context._

  override def receive: Receive = {
    case req: RequestData =>
      log.debug(s"received RequestData with requestId [${req.requestId}]")
      val reqHandler = handlerCreator(context, registry, sender(), req.requestId)
      registry ! RegisterRequestHandler(RequestHandlerReference(req.requestId, reqHandler))
      reqHandler ! req

    case resp: ResponseData =>
      log.debug(s"received ResponseData with requestId [${resp.requestId}]")
      (registry ? ResolveRequestHandler(resp.requestId)) onComplete {
        case Success(Some(reqHandler: RequestHandlerReference)) =>
          log.debug(s"forwarding response with requestId [${resp.requestId}] to actor [${reqHandler.actorRef.toString()}]")
          reqHandler.actorRef ! resp
        case Success(msg) => log.error(s"received unknown message $msg")
        case Failure(e) => log.error(s"could not find actor for request ${resp.requestId}", e)
      }
  }
}

case class CreateRequestRef(requestId: String, actorRef: ActorRef)

case class DeleteRequestRef(requestId: String)


