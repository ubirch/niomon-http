package com.ubirch.receiver.http

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import com.ubirch.kafkasupport.MessageEnvelope
import com.ubirch.receiver.actors.{RequestData, ResponseData}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}


class HttpServer(port: Int, dispatcher: ActorRef)(implicit val system: ActorSystem) {

  val log: Logger = Logger[HttpServer]
  implicit val context: ExecutionContext = system.dispatcher
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val timeout: Timeout = Timeout(10, TimeUnit.SECONDS)

  def serveHttp() {
    val route: Route = {
      path("") {
        extractRequest {
          req =>
            entity(as[Array[Byte]]) {
              input =>
                val requestId = UUID.randomUUID().toString
                val responseData = dispatcher ? RequestData(requestId, MessageEnvelope(input, getHeaders(req)))
                onComplete(responseData) {
                  case Success(res) =>
                    val result = res.asInstanceOf[ResponseData]
                    // ToDo BjB 21.09.18 : Revise Headers
                    val contentType = determineContentType(result.envelope.headers)
                    complete(HttpResponse(status = StatusCodes.Created, entity = HttpEntity(result.envelope.payload).withContentType(contentType)))
                  case Failure(e) =>
                    log.debug("dispatcher failure", e)
                    complete(StatusCodes.InternalServerError, e.getMessage)
                }
            }
        }
      } ~
        path("status") {
          complete("up")
        }
    }

    Http().bindAndHandle(route, "0.0.0.0", port) onComplete {
      case Success(v) => log.info(s"http server started: ${v.localAddress}")
      case Failure(e) => log.error("http server start failed", e)
    }
    // ToDo BjB 17.09.18 : Graceful shutdown

  }

  private val CONTENT_TYPE = "Content-Type"

  private def determineContentType(headers: Map[String, String]) = {
    ContentType.parse(headers.getOrElse(CONTENT_TYPE, "")) match {
      case Left(x) => ContentTypes.`application/octet-stream`
      case Right(x) => x
    }
  }

  private def getHeaders(req: HttpRequest): Map[String, String] = {
    Map(CONTENT_TYPE -> req.entity.contentType.mediaType.toString(),
      "Request-URI" -> req.getUri().toString)
  }
}