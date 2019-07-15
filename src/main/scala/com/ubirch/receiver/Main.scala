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
import akka.cluster.pubsub.DistributedPubSub
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.receiver.actors.{ClusterAwareRegistry, ClusterListener, Dispatcher, Registry}
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

    implicit val system: ActorSystem = createActorSystem(isCluster)
    val registry: ActorRef = createRegistry(system, isCluster)

    val kafkaUrl: String = config.getString(KAFKA_URL_PROPERTY)
    val publisher = new KafkaPublisher(kafkaUrl, config.getString(Main.KAFKA_TOPIC_INCOMING_PROPERTY))
    val dispatcher: ActorRef = system.actorOf(Props(classOf[Dispatcher], registry, actors.requestHandlerCreator(publisher)), "dispatcher")
    val listener = new KafkaListener(kafkaUrl, List(config.getString(KAFKA_TOPIC_OUTGOING_PROPERTY)), dispatcher)

    listener.startPolling()
    new HttpServer(config.getInt(HTTP_PORT_PROPERTY), dispatcher).serveHttp()
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

  private def createRegistry(system: ActorSystem, isCluster: Boolean): ActorRef = {
    if (isCluster) {
      val registry: ActorRef = system.actorOf(Props(classOf[Registry]), "registry")
      val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], DistributedPubSub(system).mediator, registry), "clusterRegistry")
      system.actorOf(Props(classOf[ClusterListener], clusterRegistry))
      clusterRegistry
    } else {
      system.actorOf(Props(classOf[Registry]), "registry")
    }
  }

  private def initPrometheus(prometheusConfig: Config): Unit = {
    DefaultExports.initialize()
    val _ = new PrometheusHttpServer(prometheusConfig.getInt("port"), true)
  }
}
