package com.ubirch.receiver.kafka

import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorRef
import cakesolutions.kafka.KafkaConsumer
import cakesolutions.kafka.KafkaConsumer.Conf
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver
import com.ubirch.receiver.ResponseData
import org.apache.kafka.clients.consumer.{ConsumerRecords, OffsetResetStrategy}
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class KafkaListener(kafkaUrl: String,
                    topic: String,
                    dispatcher: ActorRef) extends Runnable {


  private val running: AtomicBoolean = new AtomicBoolean(true)

  val consumer = KafkaConsumer(
    Conf(new StringDeserializer(),
         new ByteArrayDeserializer(),
         bootstrapServers = kafkaUrl,
         groupId = "http-receiver",
         autoOffsetReset = OffsetResetStrategy.EARLIEST)
  )

  def run(): Unit = {
    subscribe()
    while (running.get) {
      {
        val records = pollRecords
        records match {
          case Success(rcds) => deliver(rcds)
          case Failure(ex) => handleError(ex)
        }
      }
    }
    consumer.close()
  }

  def startPolling(): Unit = {
    new Thread(this).start()
  }

  def shutdown(): Unit = {
    running.set(false)
    consumer.wakeup()
  }

  def subscribe(): KafkaListener = {
    consumer.subscribe(List(topic).asJavaCollection)
    this
  }

  def pollRecords: Try[ConsumerRecords[String, Array[Byte]]] = {
    Try(consumer.poll(10))
  }

  private def deliver(rcds: ConsumerRecords[String, Array[Byte]]): Unit = {
    rcds.iterator().forEachRemaining(record => {
      dispatcher ! ResponseData(record.key(), MessageEnvelope.fromRecord(record))
    })
  }

  private def handleError(ex: Throwable): Unit = {
    ex match {
      case e: WakeupException => if (running.get()) consumer.close()
      case e: Exception => receiver.system.log.error("error polling records", e) // ToDo BjB 17.09.18 : errorhandling
    }
  }
}
