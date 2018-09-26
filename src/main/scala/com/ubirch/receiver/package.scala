package com.ubirch

import java.util.concurrent.TimeUnit

import akka.actor
import akka.actor.{ActorSystem, ExtendedActorSystem, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Publish
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.receiver.actors._
import com.ubirch.receiver.kafka.{KafkaListener, KafkaPublisher}

import scala.concurrent.{ExecutionContextExecutor, Future}

package object receiver {

  val conf: Config = ConfigFactory.load
  implicit val system: ExtendedActorSystem = Cluster(ActorSystem("http-receiver")).system
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  val pubsub: DistributedPubSub = DistributedPubSub(system)

  private val kafkaUrl: String = conf.getString("kafka.url")
  val publisher = new KafkaPublisher(kafkaUrl, conf.getString("kafka.topic.incoming"))

  private val dispatcher: actor.ActorRef = system.actorOf(Props(classOf[Dispatcher]), "dispatcher")
  val listener = new KafkaListener(kafkaUrl, conf.getString("kafka.topic.outgoing"), dispatcher)
  listener.startPolling()

  val publisherToBeRenamed = system.actorOf(Props(classOf[PublisherToBeRenamed], dispatcher, pubsub.mediator))

  def publish(requestData: RequestData): Future[ResponseData] = {
    implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)

    val rd = system.actorOf(Props(classOf[RequestActor], dispatcher, publisher), requestData.requestId)

    pubsub.mediator ! Publish("requests", CreateRequestRef(requestData.requestId, rd))
    rd ? requestData map (_.asInstanceOf[ResponseData])
  }


}
