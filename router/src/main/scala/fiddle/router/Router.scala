package fiddle.router

import java.io.ByteArrayOutputStream

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCodes}
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, StreamConverters}
import akka.util.Timeout

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class RouteRequest(req: HttpRequest)

class Router(host: String, port: Int) extends Actor with ActorLogging {
  import context.dispatcher
  implicit val timeout = Timeout(30.seconds)
  implicit val materializer = ActorMaterializer()

  val clientFlow = Http()(context.system).cachedHostConnectionPool[Int](host, port)

  var reqId = 0

  def receive = {
    case RouteRequest(req) =>
      reqId += 1
      val resp = Source.single(req -> reqId)
        .via(clientFlow)
        .runWith(Sink.head)
        .flatMap {
          case (Success(response), _) =>
            val bos = new ByteArrayOutputStream()
            val sink = StreamConverters.fromOutputStream(() => bos)
            response.entity.dataBytes.runWith(sink).map { ioResult =>
              if (ioResult.wasSuccessful)
                Right(bos.toByteArray)
              else
                Left(StatusCodes.InternalServerError)
            }
          case (Failure(ex), _) =>
            log.error(ex, s"Failed to complete request ${req.uri.path.toString}")
            Future.successful(Left(StatusCodes.InternalServerError))
        }
      resp pipeTo sender()
  }
}

object Router {
  def props(host: String, port: Int) = Props(new Router(host, port))
}
