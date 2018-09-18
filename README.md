# WIP

## HTTP-Receiver

Accepts HTTP requests and publishes request data to a kafka topic. Responds the requests as soon as there
is an according response from another topic. 

### Running locally
Working directory is `http-receiver`

Start local kafka/zookeeper docker containers: 

```
docker-compose up
```

Run `Main` class in Idea with the following environment variables set:
```
KAFKA_URL=localhost:9092
KAFKA_TOPIC_INCOMING_REQUESTS=incoming
KAFKA_TOPIC_OUTGOING_REQUESTS=incoming
```   
Note: _Setting incoming and outgoing topic the same leads to directly responding the requests with the input data.
This is handy for testing._  