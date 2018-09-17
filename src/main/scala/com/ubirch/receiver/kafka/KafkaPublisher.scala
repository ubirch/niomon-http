package com.ubirch.receiver.kafka

import cakesolutions.kafka.KafkaProducer
import cakesolutions.kafka.KafkaProducer.Conf
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer

class KafkaPublisher(kafkaUrl: String, topic: String) {

  val producer = KafkaProducer(
    Conf(new StringSerializer(),
      new StringSerializer(),
      bootstrapServers = kafkaUrl,
      acks = "all")
  )


  def send(key: String, value: String) {
    producer.send(new ProducerRecord[String, String](topic, key, value))
  }

  def shutDown(): Unit = {
    producer.close()
  }
}
