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
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}
import com.ubirch.kafka.RichAnyProducerRecord

class KafkaPublisher(kafkaUrl: String, topic: String) {

  val producer = KafkaProducer(
    Conf(new StringSerializer(),
      new ByteArraySerializer(),
      bootstrapServers = kafkaUrl,
      acks = "all")
  )


  def send(key: String, request: (Array[Byte], Map[String, String])): Unit = {
    val record = new ProducerRecord(topic, null, key, request._1).withHeaders(request._2.toSeq: _*)

    producer.send(record)
  }

  def shutDown(): Unit = {
    producer.close()
  }
}
