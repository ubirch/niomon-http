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

package com.ubirch.receiver.kafka

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorRef
import cakesolutions.kafka.KafkaConsumer
import cakesolutions.kafka.KafkaConsumer.Conf
import com.typesafe.scalalogging.Logger
import com.ubirch.receiver.actors.ResponseData
import org.apache.kafka.clients.consumer.{ConsumerRecords, OffsetResetStrategy}
import org.apache.kafka.common.errors.WakeupException
import org.apache.kafka.common.serialization.{ByteArrayDeserializer, StringDeserializer}
import com.ubirch.kafka._

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class KafkaListener(kafkaUrl: String, topics: Seq[String], dispatcher: ActorRef) extends Runnable {

  val log: Logger = Logger[KafkaListener]
  val consumer = KafkaConsumer(
    Conf(new StringDeserializer(),
      new ByteArrayDeserializer(),
      bootstrapServers = kafkaUrl,
      groupId = "niomon-http",
      autoOffsetReset = OffsetResetStrategy.LATEST)
  )
  private val running: AtomicBoolean = new AtomicBoolean(true)

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

  def subscribe(): KafkaListener = {
    consumer.subscribe(topics.asJavaCollection)
    this
  }

  def pollRecords: Try[ConsumerRecords[String, Array[Byte]]] = {
    Try(consumer.poll(Duration.ofSeconds(10))) // scalastyle:off magic.number
  }

  private def deliver(rcds: ConsumerRecords[String, Array[Byte]]): Unit = {
    rcds.iterator().forEachRemaining(record => {
      dispatcher ! ResponseData(record.key(), record.headersScala, record.value())
    })
  }

  private def handleError(ex: Throwable): Unit = {
    ex match {
      case _: WakeupException => if (running.get()) consumer.close()
      case e: Exception => log.error("error polling records", e) // ToDo BjB 17.09.18 : errorhandling
    }
  }

  def startPolling(): Unit = {
    new Thread(this).start()
  }

  def shutdown(): Unit = {
    running.set(false)
    consumer.wakeup()
  }
}
