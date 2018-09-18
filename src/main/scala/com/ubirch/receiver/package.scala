package com.ubirch

import java.util.concurrent.TimeUnit

import akka.actor
import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.receiver.Actors.{Receiver, RecordDispatcher}
import com.ubirch.receiver.kafka.{KafkaListener, KafkaPublisher}

import scala.concurrent.{ExecutionContextExecutor, Future}

package object receiver {

  final case class RequestData(requestId:String, value: Array[Byte], headers:Map[String, String])
  final case class ResponseData(requestId:String, value: Array[Byte], headers:Map[String, String])

  val conf: Config = ConfigFactory.load
  implicit val system: ActorSystem = ActorSystem("http-receiver")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher


  private val kafkaUrl: String = conf.getString("kafka.url")
  val publisher = new KafkaPublisher(kafkaUrl, conf.getString("kafka.topic.incoming"))

  private val rd: actor.ActorRef = system.actorOf(Props[RecordDispatcher], "recordDispatcher")
  val listener = new KafkaListener(kafkaUrl, conf.getString("kafka.topic.outgoing"), rd)
  listener.startPolling()


  def publish(requestData: RequestData): Future[String] = {
    implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)

    val rev = system.actorOf(Props[Receiver], requestData.requestId)

    rev ? requestData map (_.asInstanceOf[String])
  }


}
