package scalafiddle.router

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.ActorMaterializer
import scalafiddle.shared._
import upickle.default._

import scala.concurrent.duration._

case object WatchCompiler

case class CompilerPing(id: String)

class CompilerService(out: ActorRef, manager: ActorRef, scalaVersion: String) extends Actor with ActorLogging {
  implicit val materializer = ActorMaterializer()(context)
  import context.dispatcher

  val id          = UUID.randomUUID().toString
  var lastSeen    = System.currentTimeMillis()
  val watchdog    = context.system.scheduler.schedule(1.minutes, 1.minutes, self, WatchCompiler)

  override def preStart(): Unit = {
    super.preStart()
    log.info(s"CompilerService $id starting (Scala $scalaVersion)")
    manager ! RegisterCompiler(id, context.self, scalaVersion)
  }

  def sendOut(msg: CompilerMessage): Unit = {
    out ! TextMessage.Strict(write[CompilerMessage](msg))
  }

  def receive = {
    case msg: TextMessage =>
      msg.textStream.runReduce(_ + _).map { text =>
        read[CompilerMessage](text) match {
          case Ping =>
            lastSeen = System.currentTimeMillis()
            // inform the manager that we're alive!
            manager ! CompilerPing(id)
            sendOut(Pong)
          case CompilerReady =>
            manager ! (id -> CompilerReady)
          case resp: CompilerResponse =>
            manager ! (id -> resp)
          case m =>
            log.error(s"Received unknown compiler message $m")
        }
      } recover {
        case e =>
          log.error(e, s"Error while processing incoming message")
      }

    case msg: CompilerMessage =>
      sendOut(msg)

    case WatchCompiler =>
      val now = System.currentTimeMillis()
      // if we haven't heard from the compiler, stop this service
      if (now - lastSeen > 60 * 1000) {
        log.error(s"Have not seen compiler $id in ${(now - lastSeen) / 1000} seconds, terminating compiler service")
        context.stop(self)
      }

    case other =>
      log.error(s"Received something unexpected ${other.toString.take(150)}")
  }

  override def postStop(): Unit = {
    log.info(s"CompilerService $id stopping")
    watchdog.cancel()
    manager ! UnregisterCompiler(id)
    super.postStop()
  }
}

object CompilerService {
  def props(out: ActorRef, manager: ActorRef, scalaVersion: String) = Props(new CompilerService(out, manager, scalaVersion))
}
