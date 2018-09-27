package com.ubirch

import akka.actor
import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.receiver.actors._
import com.ubirch.receiver.kafka.{KafkaListener, KafkaPublisher}

import scala.concurrent.ExecutionContextExecutor

package object receiver {

  val conf: Config = ConfigFactory.load
  implicit val system: ExtendedActorSystem = Cluster(ActorSystem("http-receiver")).system
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  private val kafkaUrl: String = conf.getString("kafka.url")
  val publisher = new KafkaPublisher(kafkaUrl, conf.getString("kafka.topic.incoming"))

  val dispatcher: actor.ActorRef = system.actorOf(Props(classOf[Dispatcher], DistributedPubSub(system).mediator), "dispatcher")
  val listener = new KafkaListener(kafkaUrl, conf.getString("kafka.topic.outgoing"), dispatcher)
  listener.startPolling()

}
