package fiddle.router

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.http.scaladsl.model.ws.TextMessage

class CompilerService(out: ActorRef) extends Actor with ActorLogging {

  override def preStart(): Unit = {
    log.debug("CompilerService starting")
    out ! TextMessage("Welcome!")
    super.preStart()
  }

  def receive = {
    case msg: TextMessage.Strict =>
      log.debug(s"Received: ${msg.text}")
  }

  override def postStop(): Unit = {

  }
}

object CompilerService {
  def props(out: ActorRef) = Props(new CompilerService(out))
}