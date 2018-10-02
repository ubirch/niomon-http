package com.ubirch.receiver

import akka.actor.{ActorRef, ActorSystem, Address, AddressFromURIString, Props}
import akka.cluster.Cluster
import akka.cluster.pubsub.DistributedPubSub
import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.receiver.actors.{ClusterAwareRegistry, ClusterListener, Dispatcher, Registry}
import com.ubirch.receiver.http.HttpServer
import com.ubirch.receiver.kafka.{KafkaListener, KafkaPublisher}

import scala.concurrent.ExecutionContextExecutor

object Main {
  val DEPLOYMENT_MODE_ENV = "DEPLOYMENT_MODE"

  val KAFKA_URL_PROPERTY = "kafka.url"
  val KAFKA_TOPIC_INCOMING_PROPERTY = "kafka.topic.incoming"
  val KAFKA_TOPIC_OUTGOING_PROPERTY = "kafka.topic.outgoing"
  val HTTP_PORT_PROPERTY = "http.port"

  def main(args: Array[String]) {

    val isCluster = Option(System.getProperty(DEPLOYMENT_MODE_ENV)).forall(!_.equalsIgnoreCase("local"))
    val config: Config = loadConfig(isCluster)

    implicit val system: ActorSystem = createActorSystem(config, isCluster)
    implicit val executionContext: ExecutionContextExecutor = system.dispatcher
    val registry: ActorRef = createRegistry(system, isCluster)

    val kafkaUrl: String = config.getString(KAFKA_URL_PROPERTY)
    val publisher = new KafkaPublisher(kafkaUrl, config.getString(Main.KAFKA_TOPIC_INCOMING_PROPERTY))
    val dispatcher: ActorRef = system.actorOf(Props(classOf[Dispatcher], registry, actors.requestHandlerCreator(publisher)), "dispatcher")
    val listener = new KafkaListener(kafkaUrl, config.getString(KAFKA_TOPIC_OUTGOING_PROPERTY), dispatcher)

    listener.startPolling()
    new HttpServer(config.getInt(HTTP_PORT_PROPERTY), dispatcher).serveHttp()
  }

  private def loadConfig(cluster: Boolean) = {
    if (cluster)
      ConfigFactory.load.getConfig("cluster").withFallback(ConfigFactory.load())
    else
      ConfigFactory.load()
  }

  private def createActorSystem(config: Config, isCluster: Boolean) = {
    if (isCluster) {
      val addresses = sys.env.getOrElse("CLUSTER_SEED_NODES", "").split(',').toList.map(AddressFromURIString(_))
      val cluster = Cluster(ActorSystem("http-receiver", config))
      cluster.joinSeedNodes(addresses)
      cluster.system

    } else {
      ActorSystem("http-receiver", config)
    }
  }

  private def createRegistry(system: ActorSystem, isCluster: Boolean): ActorRef = {
    if (isCluster) {
      val registry: ActorRef = system.actorOf(Props(classOf[Registry]), "registry")
      val clusterRegistry = system.actorOf(Props(classOf[ClusterAwareRegistry], DistributedPubSub(system).mediator, registry), "clusterRegistry")
      system.actorOf(Props(classOf[ClusterListener],clusterRegistry ))
      clusterRegistry
    } else {
      system.actorOf(Props(classOf[Registry]), "registry")
    }
  }
}
