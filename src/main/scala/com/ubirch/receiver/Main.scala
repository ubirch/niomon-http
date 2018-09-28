package com.ubirch.receiver

import akka.actor
import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.cluster.Cluster
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.receiver.actors.{Dispatcher, Registry}
import com.ubirch.receiver.http.HttpServer
import com.ubirch.receiver.kafka.KafkaListener

import scala.concurrent.ExecutionContextExecutor

object Main {

  def main(args: Array[String]) {
    val conf: Config = ConfigFactory.load
    implicit val system: ExtendedActorSystem = Cluster(ActorSystem("http-receiver")).system

    implicit val executionContext: ExecutionContextExecutor = system.dispatcher

    val kafkaUrl: String = conf.getString("kafka.url")

    val registry: actor.ActorRef = system.actorOf(Props(classOf[Registry]), "registry")
    val dispatcher: actor.ActorRef = system.actorOf(Props(classOf[Dispatcher], registry, actors.requestHandlerCreator), "dispatcher")
    val listener = new KafkaListener(kafkaUrl, conf.getString("kafka.topic.outgoing"), dispatcher)
    listener.startPolling()

    new HttpServer(conf.getInt("http.port"), dispatcher).serveHttp()
  }
}
