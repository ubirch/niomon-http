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
3. the `Dispatcher` creates a `HttpRequestHandler` with a reference to the `Route` for each request, registers it in the `Registry` and sends
the request data to it.
4. the `HttpRequestHandler` sends the request data to the `KafkaPublisher` and waits for response data.
5. `KafkaPublisher` is sending the data to the `incoming` topic
6. `KafkaListener` is consuming the `outgoing` topic
7. when response data arrives, `KafkaListener` is sending it to the `Dispatcher`
8. `Dispatcher` resolves the according `HttpRequestHandler` based on the `requestId` via the `Registry`
9. `Dispatcher` sends response data to the `HttpRequestHandler` (which is waiting for that since step 5.)
10. `HttpRequestHandler` sends the response data back to the `Route` 
11. `Route` is responding the http request with the response data
12. `HttpRequestHandler` unregisters itself in the `Registry` (which causes `Registry` to stop it)

**Cluster Details**

* when running as cluster, at least 2 (better 3) nodes (instances/pods) have to run. 
* those nodes act as `akka-cluster-seed-nodes` for managing the cluster
* registration (above step 5.) of `HttpRequestHandler` is done via the `ClusterAwareRegistry` which delegates to `Reqistry` and 
publishes the registration via `akka.cluster.pubsub` to the other nodes in the cluster. This is done 
to resolve (above step 8.) the according `HttpRequestHandler` for response data from kafka, because of the 
fact that the response data might not arrive on the same node as the node which has to respond the request. 
* `ClusterListener` listens for new members joining the cluster. Whenever that happens it lets `ClusterAwareRegistry` 
publish all known `HttpRequestHandler` registrations to all nodes. This leads to every `Registry` knowing 
all registrations.

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