package com.ubirch.receiver.kafka

import cakesolutions.kafka.KafkaProducer
import cakesolutions.kafka.KafkaProducer.Conf
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.{BytesSerializer, StringSerializer}
import org.apache.kafka.common.utils.Bytes

import scala.collection.JavaConverters._

class KafkaPublisher(kafkaUrl: String, topic: String) {

  val producer = KafkaProducer(
    Conf(new StringSerializer(),
         new BytesSerializer(),
         bootstrapServers = kafkaUrl,
         acks = "all")
  )


  def send(key: String, value: Array[Byte], headers: Map[String, String]) {
    val kafkaHeaders: Iterable[Header] = asKafkaHeaders(headers)
    val record = new ProducerRecord[String, Bytes](topic, null, key, new Bytes(value), kafkaHeaders.asJava)

    producer.send(record)
  }

  private def asKafkaHeaders(headers: Map[String, String])= {
    headers.map {
      case (k: String, v: Any) => new RecordHeader(k, v.getBytes)
    }
  }

  def shutDown(): Unit = {
    producer.close()
  }
}
