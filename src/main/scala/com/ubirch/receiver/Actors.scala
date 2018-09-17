package com.ubirch.receiver

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Actors {

  import akka.actor.{Actor, ActorRef}
  import akka.event.Logging


  class RecordDispatcher extends Actor {
    val log = Logging(context.system, this)

    override def receive: Receive = {
      case KV(key, value) =>
        val path = s"user/$key"

        val t0 = System.currentTimeMillis()
        val target = context.system.actorSelection(path)

        target.resolveOne(FiniteDuration(10, TimeUnit.MILLISECONDS))
          .onComplete {
            case Success(ref) => ref ! KV(key, value)
            case Failure(f) => log.warning(s"could not resolve actor to handle response for requestId [$key]", f)
          }
        val t1 = System.currentTimeMillis()

        log.debug(s"finding target $path took ${t1 - t0}ms")
    }

  }


  class Receiver extends Actor {
    val log = Logging(context.system, this)

    import context._

    def receive: PartialFunction[Any, Unit] = {

      case KV(k, v) =>
        log.debug(s"received input $v")
        publisher.send(key = k, value = v)
        become(outgoing(sender()))

    }

    private def outgoing(returnTo: ActorRef): Receive = {

      case KV(_, v) =>
        log.debug(s"received output $v")
        returnTo ! s"from kafka with love: $v"
        context.stop(self)
    }
  }

}
