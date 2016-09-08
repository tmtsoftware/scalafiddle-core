package fiddle.router

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.ws.TextMessage
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import fiddle.shared._
import upickle.default._

class CompilerService(out: ActorRef, manager: ActorRef) extends Actor with ActorLogging {
  implicit val materializer = ActorMaterializer()(context)
  import context.dispatcher

  val id = UUID.randomUUID().toString

  override def preStart(): Unit = {
    super.preStart()
    log.debug(s"CompilerService $id starting")
    manager ! RegisterCompiler(id, context.self)
  }

  def sendOut(msg: CompilerMessage): Unit = {
    out ! TextMessage.Strict(write[CompilerMessage](msg))
  }

  def receive = {
    case msg: TextMessage =>
      msg.textStream.runWith(Sink.reduce[String](_ + _)).map { text =>
        read[CompilerMessage](text) match {
          case Ping =>
            sendOut(Pong)
          case CompilerReady =>
            manager ! (id -> CompilerReady)
          case resp: CompilerResponse =>
            manager ! (id -> resp)
          case _ =>
        }
      }

    case msg: CompilerMessage =>
      sendOut(msg)

    case other =>
      log.error(s"Received something unexpected ${other.toString.take(150)}")
  }

  override def postStop(): Unit = {
    log.debug(s"CompilerService $id stopping")
    manager ! UnregisterCompiler(id)
    super.postStop()
  }
}

object CompilerService {
  def props(out: ActorRef, manager: ActorRef) = Props(new CompilerService(out, manager))
}