package com.ubirch.receiver.kafka

import cakesolutions.kafka.KafkaProducer
import cakesolutions.kafka.KafkaProducer.Conf
import com.ubirch.kafkasupport.MessageEnvelope
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.{ByteArraySerializer, StringSerializer}

class KafkaPublisher(kafkaUrl: String, topic: String) {

  val producer = KafkaProducer(
    Conf(new StringSerializer(),
      new ByteArraySerializer(),
      bootstrapServers = kafkaUrl,
      acks = "all")
  )


  def send(key: String, envelope: MessageEnvelope[Array[Byte]]): Unit = {
    val record = MessageEnvelope.toRecord(topic, key, envelope)

    producer.send(record)
  }

  private def asKafkaHeaders(headers: Map[String, String]) = {
    headers.map {
      case (k: String, v: Any) => new RecordHeader(k, v.getBytes)
    }
  }

  def shutDown(): Unit = {
    producer.close()
  }
}
