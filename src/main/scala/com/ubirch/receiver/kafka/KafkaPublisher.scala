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

import cakesolutions.kafka.KafkaProducer
import cakesolutions.kafka.KafkaProducer.Conf
import com.ubirch.kafka.RichAnyProducerRecord
import com.ubirch.niomon.healthcheck.{Checks, HealthCheckServer}
import org.apache.kafka.clients.producer.{ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

import scala.concurrent.{ExecutionContext, Future}

class KafkaPublisher(kafkaUrl: String, topic: String, healthCheckServer: HealthCheckServer) {

  val producer: KafkaProducer[String, Array[Byte]] = KafkaProducer(
    Conf(new StringSerializer(),
      new ByteArraySerializer(),
      bootstrapServers = kafkaUrl,
      acks = "all"
    )
  )

  healthCheckServer.setReadinessCheck(Checks.kafka("kafka-producer", producer.producer, connectionCountMustBeNonZero = false))

  def send(requestId: String, payload: Array[Byte], headers: Map[String, String])(implicit ec: ExecutionContext): Future[PublisherSuccess] = {
    val record = new ProducerRecord[String, Array[Byte]](topic, payload)
      .withHeaders(headers.toSeq: _*)
      .withRequestIdHeader()(requestId)
    producer.send(record).transform(PublisherSuccess(_, requestId), PublisherException(_, requestId))
  }

  def shutDown(): Unit = {
    producer.close()
  }
}

final case class PublisherException(cause: Throwable, requestId: String)
  extends Exception("kafka publisher exception", cause)

final case class PublisherSuccess(recordMetadata: RecordMetadata, requestId: String)
