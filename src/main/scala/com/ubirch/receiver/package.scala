package com.ubirch

import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.receiver.kafka.KafkaPublisher

package object receiver {
  val conf: Config = ConfigFactory.load
  val kafkaUrl: String = conf.getString("kafka.url")
  val publisher = new KafkaPublisher(kafkaUrl, conf.getString("kafka.topic.incoming"))

}
