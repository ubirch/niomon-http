package com.ubirch.receiver

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Actors {

  import akka.actor.{Actor, ActorRef}
  import akka.event.Logging


  class ResponseDispatcher extends Actor {
    val log = Logging(context.system, this)

    override def receive: Receive = {
      case resp:ResponseData =>
        val path = s"user/${resp.requestId}"

        val t0 = System.currentTimeMillis()
        val target = context.system.actorSelection(path)

        target.resolveOne(FiniteDuration(10, TimeUnit.MILLISECONDS))
          .onComplete {
            case Success(ref) => ref ! resp
            case Failure(f) => log.warning(s"could not resolve actor to handle response for requestId [${resp.requestId}]", f)
          }
        val t1 = System.currentTimeMillis()

        log.debug(s"finding target $path took ${t1 - t0}ms")
    }

  }


  class RequestDispatcher extends Actor {
    import context._
    val log = Logging(context.system, this)

    def receive: PartialFunction[Any, Unit] = {

      case RequestData(k, e) =>
        log.debug(s"received input [${e.payload.mkString}]")
        publisher.send(key = k, e)
        become(outgoing(sender()))

    }

    private def outgoing(returnTo: ActorRef): Receive = {

      case reponse: ResponseData =>
        returnTo ! reponse
        context.stop(self)
    }
  }

}
