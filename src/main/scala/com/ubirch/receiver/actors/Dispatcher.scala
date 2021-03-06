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

import java.util.Base64
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, DiagnosticActorLogging, ExtendedActorSystem}
import akka.event.Logging
import akka.event.Logging.MDC
import akka.util.Timeout

/**
  * Creates a HttpRequestHandler for each incoming RequestData from HTTP.
  * Routes the responses from kafka to the formerly created HttpRequestHandler
  */
class Dispatcher(handlerCreator: HttpRequestHandlerCreator) extends Actor with DiagnosticActorLogging {
  implicit val timeout: Timeout = Timeout(100, TimeUnit.MILLISECONDS) // scalastyle:off magic.number

  import context._

  override def mdc(currentMessage: Any): MDC = currentMessage match {
    case r: RequestData => Map("requestId" -> r.requestId) ++
      (if (log.isDebugEnabled) Map(
        // does not work, flattens the object and that breaks the index
        //        "headers" -> r.headers.asJava,
        "data" -> Base64.getEncoder.encodeToString(r.payload))
      else Nil)
    case r: ResponseData => Map("requestId" -> r.requestId)
    case _ => Logging.emptyMDC
  }

  override def receive: Receive = {
    case req: RequestData =>
      log.debug(s"received request with requestId [${req.requestId}]")
      handlerCreator(context, sender(), req.requestId) ! req

    case resp: ResponseData =>
      log.debug(s"responding to requestId [${resp.requestId}]")

      val maybeSerializedActorRef = resp.headers.get("http-request-handler-actor")

      if (maybeSerializedActorRef.isEmpty) {
        log.error(s"Found a response without a handler! resp: [$resp]")
      }

      maybeSerializedActorRef.foreach { actorRefStr =>
        log.debug(s"trying to deserialize actor ref: [$actorRefStr]")
        val handlerRef = system.asInstanceOf[ExtendedActorSystem].provider.resolveActorRef(actorRefStr)
        handlerRef ! resp
      }
  }
}

case class CreateRequestRef(requestId: String, actorRef: ActorRef)

case class DeleteRequestRef(requestId: String)


