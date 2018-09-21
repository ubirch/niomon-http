package com.ubirch

import java.util.concurrent.TimeUnit

import akka.actor
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver.Actors.{RequestDispatcher, ResponseDispatcher}
import com.ubirch.receiver.kafka.{KafkaListener, KafkaPublisher}

import scala.concurrent.{ExecutionContextExecutor, Future}

package object receiver {

  final case class RequestData(requestId:String, envelope:MessageEnvelope[Array[Byte]])
  final case class ResponseData(requestId:String, envelope:MessageEnvelope[Array[Byte]])

  val conf: Config = ConfigFactory.load
  implicit val system: ActorSystem = ActorSystem("http-receiver")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher


  private val kafkaUrl: String = conf.getString("kafka.url")
  val publisher = new KafkaPublisher(kafkaUrl, conf.getString("kafka.topic.incoming"))

  private val responseDispatcher: actor.ActorRef = system.actorOf(Props[ResponseDispatcher], "recordDispatcher")
  val listener = new KafkaListener(kafkaUrl, conf.getString("kafka.topic.outgoing"), responseDispatcher)
  listener.startPolling()


  def publish(requestData: RequestData): Future[String] = {
    implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)

    val dispatcher = system.actorOf(Props[RequestDispatcher], requestData.requestId)

    dispatcher ? requestData map (_.asInstanceOf[String])
  }


}
