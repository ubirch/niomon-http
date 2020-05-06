# WIP

# niomon-http

#### Overview

niomon-http acts as (one) entry point to the ubirch ingestion pipeline. 
It is accepting http-requests and publishes the request data to a kafka topic for later processing. 
It is responding the requests with the result of the processing within the pipeline.
Correlation of request data and response data is done using a unique `requestId` which is generated for
each request and sent as header to kafka together with the request data.

niomon-http is capable of running with multiple instances as a akka cluster. Which is the default
deployment strategy in kubernetes.

#### Implementation

**Basic workflow**
1. For each incoming request a `requestId` is generated. 
2. The http `Route` is handing the request data to the `Dispatcher` actor.
3. the `Dispatcher` creates a `HttpRequestHandler` with a reference to the `Route` for each request, and sends
the request data to it.
4. the `HttpRequestHandler` adds a serialized reference to itself as a kafka header, sends the request data to the 
`KafkaPublisher` and waits for response data.
5. `KafkaPublisher` is sending the data to the `incoming` topic
6. `KafkaListener` is consuming the `outgoing` topic
7. when response data arrives, `KafkaListener` is sending it to the `Dispatcher`
8. `Dispatcher` resolves the according `HttpRequestHandler` by deserializing the actor ref attached to the response
9. `Dispatcher` sends response data to the `HttpRequestHandler` (which is waiting for that since step 5.)
10. `HttpRequestHandler` sends the response data back to the `Route` 
11. `Route` is responding the http request with the response data

**Cluster Details**
The cluster uses kubernetes for autodiscovery of its members.

**Notes on Kafka**

* Due to the implementation described above, the configuration of topics (namely the count of partitions per
topic) can be handled transparently to the niomon-http.
* There is no need to implement a custom `org.apache.kafka.clients.producer.Partitioner` for the
kafka producer or a `partition.assignment.strategy` for the kafka consumer. 
* for best scaling the number of partitions for the incoming and the outcoming topic should be equal or a multiple
of the deployed instances (nodes) of the niomon-http.

#### Frameworks and Dependencies

* akka-http and akka-streams for handling HTTP requests
* akka-cluster for cluster functionality
* apache kafka (with `net.cakesolutions:scala-kafka-client` as API) for working with kafka

## running locally

#### in Idea

Start local kafka/zookeeper docker containers: 

```
# in project root run:
docker-compose -f docker-compose-local-kafka.yml up --force-recreate
```

Run `Main` class in Idea with the following environment variables set:
```
KAFKA_URL=localhost:9092
KAFKA_TOPIC_HTTP2INC_REQUESTS=incoming 
KAFKA_TOPIC_OUT2HTTP_REQUESTS=incoming
DEPLOYMENT_MODE=local
```
**Note 1**: _Setting incoming and outgoing topic the same leads to directly responding the requests with the input data.
This is handy for testing._  

**Note 2**: _Setting `DEPLOYMENT_MODE=local` starts the receiver without cluster functionality._  

HTTP endpoint is now reachable via `http://localhost:8080/`.

A sample request can look like:

```
$ curl -v "http://localhost:8080/" -H 'Content-Type: application/json' -d $'{"version": 1234}'
*   Trying ::1...
* TCP_NODELAY set
* Connected to localhost (::1) port 8080 (#0)
> POST / HTTP/1.1
> Host: localhost:8080
> User-Agent: curl/7.54.0
> Accept: */*
> Content-Type: application/json
> Content-Length: 17
>
* upload completely sent off: 17 out of 17 bytes
< HTTP/1.1 201 Created
< Server: akka-http/10.1.4
< Date: Tue, 02 Oct 2018 08:24:43 GMT
< Content-Type: application/json
< Content-Length: 17
<
* Connection #0 to host localhost left intact
{"version": 1234}
```

#### with docker-compose

Starting with docker-compose file will start the HTTP Receiver as 2 node akka cluster 
together with kafka and zookeeper. **Again incoming and outgoing topics are the same, so that 
the responses will echo the request data.**

1. build with `mvn package`
2. in directory `niomon-http` run `docker-compose up --build --force-recreate`
3. afterwards stop with `ctrl-c` and `docker-compose down` (which removes the containers) 
 
HTTP endpoints of both receivers are now reachable via `http://localhost:8080/` and `http://localhost:8081/`.
 
Two sample requests against the two nodes can look like this:
 
 ```
 $ curl "http://localhost:8080/" -H 'Content-Type: application/json' -d $'{"node": "one"}'
 {"node": "one"}
 $ curl "http://localhost:8081/" -H 'Content-Type: application/json' -d $'{"node": "two"}'
 {"node": "two"}
 $

```

## Deployment in kubernetes

**TBD**

## Akka cluster monitoring

This project contains two sets of tools to support akka clustering monitoring. The first one is a set of scripts that allow
to monitor the state of the akka cluster through jmx. 

### JMX

These tools are located under the folder 'scripts'. 

**cluster_leader.sh**: It returns the current leader in the cluster.
 
**cluster_members.sh**: It returns the current members seen by the current node.

cluster_pods.sh: It returns the current pods that have been deployed. The result is sorted by IP. This is particularly helpful as the
smaller IP is usually the one selected as the leader when the cluster is being formed. That is to say, that is very likely that
the first pod is the current leader. You can double confirm this by running the 'cluster_leader' script.

**cluster_seed_nodes.sh**: It returns a list current seed nodes and their status as seen by the current node.
 
**cluster_status.sh**: It returns the current status of cluster.

**forward_akka_mng_any.sh**: It queries for the current deployed pods and randomly selects one and starts port forwarding the akka management port. 8558.

**forward_any.sh**: It queries for the current deployed pods and randomly selects one and starts port forwarding on the provided port number.

**forward_jmx_any.sh**: It queries for the current deployed pods and randomly selects one and starts port forwarding the java jmx port: 9010.


**Note:** In order to use one or more of the monitoring scripts, the corresponding port forwarding should have been started.

cluster_leader.sh, cluster_members, cluster_status depend on the forward_jmx_any script. 
cluster_seed_nodes depends on forward_akka_mng_any 

#### Example

_Start Port-forwarding_

```bash
carlos@saturn:~/sources/ubirch/niomon-all/niomon/http/scripts$ ./forward_jmx_any.sh dev ubirch-dev
Forwarding - "niomon-http-deployment-75b64ff4b-mbjkm" @ "10.244.0.63" - leader=yes
Forwarding from 127.0.0.1:9010 -> 9010
Forwarding from [::1]:9010 -> 9010
```

_Get leader_

```bash
carlos@saturn:~/sources/ubirch/niomon-all/niomon/http/scripts$ ./cluster_leader.sh dev ubirch-dev
Leader = akka.tcp://niomon-http@10.244.0.63:2551;
```

_Get members_

```bash
carlos@saturn:~/sources/ubirch/niomon-all/niomon/http/scripts$ ./cluster_members.sh dev ubirch-dev
Members = akka.tcp://niomon-http@10.244.0.63:2551,akka.tcp://niomon-http@10.244.1.77:2551,akka.tcp://niomon-http@10.244.2.111:2551;
```

_Get current cluster status_

```bash
carlos@saturn:~/sources/ubirch/niomon-all/niomon/http/scripts$ ./cluster_status.sh dev ubirch-dev
ClusterStatus = {
  "members": [
    {
      "address": "akka.tcp://niomon-http@10.244.0.63:2551",
      "roles": [
        "dc-default"
      ],
      "status": "Up"
    },
    {
      "address": "akka.tcp://niomon-http@10.244.1.77:2551",
      "roles": [
        "dc-default"
      ],
      "status": "Up"
    },
    {
      "address": "akka.tcp://niomon-http@10.244.2.111:2551",
      "roles": [
        "dc-default"
      ],
      "status": "Up"
    }
  ],
  "self-address": "akka.tcp://niomon-http@10.244.0.63:2551",
  "unreachable": []
}
;
```

_Start Port-forwarding_

```bash
carlos@saturn:~/sources/ubirch/niomon-all/niomon/http/scripts$ ./forward_akka_mng_any.sh dev ubirch-dev
Forwarding - "niomon-http-deployment-75b64ff4b-b4lcp" @ "10.244.1.77" - leader=no
Forwarding from 127.0.0.1:8558 -> 8558
Forwarding from [::1]:8558 -> 8558
```

_Get pods_

```bash
carlos@saturn:~/sources/ubirch/niomon-all/niomon/http/scripts$ ./cluster_pods.sh dev ubirch-dev
[
  {
    "namespace": "ubirch-dev",
    "pod": "niomon-http-deployment-75b64ff4b-mbjkm",
    "podIP": "10.244.0.63",
    "phase": "Running"
  },
  {
    "namespace": "ubirch-dev",
    "pod": "niomon-http-deployment-75b64ff4b-b4lcp",
    "podIP": "10.244.1.77",
    "phase": "Running"
  },
  {
    "namespace": "ubirch-dev",
    "pod": "niomon-http-deployment-75b64ff4b-fbbst",
    "podIP": "10.244.2.111",
    "phase": "Running"
  }
]
```

_Get seed nodes_

```bash
carlos@saturn:~/sources/ubirch/niomon-all/niomon/http/scripts$ ./cluster_seed_nodes.sh dev ubirch-dev
{
  "seedNodes": [
    {
      "node": "akka.tcp://niomon-http@10.244.0.63:2551",
      "nodeUid": 849117139,
      "roles": [
        "dc-default"
      ],
      "status": "Up"
    },
    {
      "node": "akka.tcp://niomon-http@10.244.1.77:2551",
      "nodeUid": -947453988,
      "roles": [
        "dc-default"
      ],
      "status": "Up"
    },
    {
      "node": "akka.tcp://niomon-http@10.244.2.111:2551",
      "nodeUid": -1949926541,
      "roles": [
        "dc-default"
      ],
      "status": "Up"
    }
  ],
  "selfNode": "akka.tcp://niomon-http@10.244.1.77:2551"
}
```


