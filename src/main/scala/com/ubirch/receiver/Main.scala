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

package com.ubirch.receiver

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.cluster.Cluster
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.niomon.healthcheck.{Checks, HealthCheckServer}
import com.ubirch.receiver.actors.Dispatcher
import com.ubirch.receiver.http.HttpServer
import com.ubirch.receiver.kafka.{KafkaListener, KafkaPublisher}
import io.prometheus.client.exporter.{HTTPServer => PrometheusHttpServer}
import io.prometheus.client.hotspot.DefaultExports

object Main {
  val DEPLOYMENT_MODE_ENV = "DEPLOYMENT_MODE"

  val KAFKA_URL_PROPERTY = "kafka.url"
  val KAFKA_TOPIC_INCOMING_PROPERTY = "kafka.topic.incoming"
  val KAFKA_TOPIC_OUTGOING_PROPERTY = "kafka.topic.outgoing"
  val HTTP_PORT_PROPERTY = "http.port"

  def main(args: Array[String]): Unit = {
    val isCluster = sys.env.get(DEPLOYMENT_MODE_ENV).forall(!_.equalsIgnoreCase("local"))
    val config: Config = ConfigFactory.load()

    initPrometheus(config.getConfig("prometheus"))
    val healthCheckServer = initHealthCheckServer(config.getConfig("health-check"))

    implicit val system: ActorSystem = createActorSystem(isCluster)

    val kafkaUrl: String = config.getString(KAFKA_URL_PROPERTY)
    val publisher = new KafkaPublisher(kafkaUrl, config.getString(Main.KAFKA_TOPIC_INCOMING_PROPERTY), healthCheckServer)
    val dispatcher: ActorRef = system.actorOf(Props(classOf[Dispatcher], actors.requestHandlerCreator(publisher)), "dispatcher")
    val listener = new KafkaListener(kafkaUrl, List(config.getString(KAFKA_TOPIC_OUTGOING_PROPERTY)), dispatcher, healthCheckServer)

    listener.startPolling()
    new HttpServer(config.getInt(HTTP_PORT_PROPERTY), dispatcher).serveHttp()

    healthCheckServer.setLivenessCheck(Checks.ok("business-logic"))
    healthCheckServer.setReadinessCheck(Checks.ok("business-logic"))
  }

  private def createActorSystem(isCluster: Boolean) = {
    if (isCluster) {
      val system = ActorSystem("niomon-http")
      Cluster(system)
      AkkaManagement(system).start()
      ClusterBootstrap(system).start()

      system
    } else {
      ActorSystem("niomon-http")
    }
  }

  private def initPrometheus(prometheusConfig: Config): Unit = {
    DefaultExports.initialize()
    val _ = new PrometheusHttpServer(prometheusConfig.getInt("port"), true)
  }

  private def initHealthCheckServer(config: Config): HealthCheckServer = {
    val s = new HealthCheckServer(Map(), Map())

    s.setLivenessCheck(Checks.process())
    s.setReadinessCheck(Checks.process())

    s.setLivenessCheck(Checks.notInitialized("business-logic"))
    s.setReadinessCheck(Checks.notInitialized("business-logic"))

    s.setReadinessCheck(Checks.notInitialized("kafka-consumer"))
    s.setReadinessCheck(Checks.notInitialized("kafka-producer"))

    if (config.getBoolean("enabled")) {
      s.run(config.getInt("port"))
    }

    s
  }
}
