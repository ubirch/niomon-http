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


  implicit val timeout: Timeout = Timeout(100, TimeUnit.MILLISECONDS)

  import context._

  override def receive: Receive = {
    case req: RequestData =>
      log.debug(s"received RequestData with requestId [${req.requestId}]")
      implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)
      val reqHandler = handlerCreator(context, registry, sender(), req.requestId)
      registry ! RegisterRequestHandler(RequestHandlerReference(req.requestId, reqHandler))
      reqHandler ! req

    case resp: ResponseData =>
      log.debug(s"received ResponseData with requestId [${resp.requestId}]")
      (registry ? ResolveRequestHandler(resp.requestId)) onComplete {
        case Success(Some(reqHandler: RequestHandlerReference)) =>
          log.debug(s"forwarding response with requestId [${resp.requestId}] to actor [${reqHandler.actorRef.toString()}]")
          reqHandler.actorRef ! resp
        case Failure(e) => log.error(s"could not find actor for request ${resp.requestId}", e)
      }
  }
}

case class CreateRequestRef(requestId: String, actorRef: ActorRef)

case class DeleteRequestRef(requestId: String)


