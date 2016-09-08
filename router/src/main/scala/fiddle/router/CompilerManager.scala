package fiddle.router

import akka.actor._
import fiddle.shared._
import upickle.default._

import scala.collection.mutable

case class RegisterCompiler(id: String, compilerService: ActorRef)

case class UnregisterCompiler(id: String)

case class UpdateState(id: String, newState: CompilerState)

case class CancelCompilation(id: String)

case class CancelCompletion(id: String)

class CompilerManager extends Actor with ActorLogging {
  import CompilerManager._

  val compilers = mutable.Map.empty[String, CompilerInfo]
  var compilerQueue = mutable.Queue.empty[(CompilerRequest, ActorRef)]
  val compilationPending = mutable.Map.empty[String, ActorRef]
  var currentLibs = loadLibraries(Config.extLibs, Config.defaultLibs)

  def now = System.currentTimeMillis()

  def loadLibraries(uri: String, defaultLibs: Seq[String]): Seq[ExtLib] = {
    val data = if (uri.startsWith("file:")) {
      // load from file system
      scala.io.Source.fromFile(uri.drop(5), "UTF-8").mkString
    } else if (uri.startsWith("http")) {
      // load from internet
      System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36")
      scala.io.Source.fromURL(uri, "UTF-8").mkString
    } else {
      // load from resources
      scala.io.Source.fromInputStream(getClass.getResourceAsStream(uri), "UTF-8").mkString
    }
    val extLibs = read[Seq[String]](data)
    (extLibs ++ defaultLibs).map(ExtLib(_))
  }

  def selectCompiler(): Option[CompilerInfo] = {
    compilers.values.find(_.state == CompilerState.Ready)
  }

  def updateCompilerState(id: String, newState: CompilerState) = {
    if (compilers.contains(id)) {
      compilers.update(id, compilers(id).copy(state = newState))
    }
  }

  def processQueue(): Unit = {
    if(compilerQueue.nonEmpty) {
      selectCompiler() match {
        case Some(compilerInfo) =>
          val (req, source) = compilerQueue.dequeue()
          updateCompilerState(compilerInfo.id, CompilerState.Compiling)
          compilationPending += compilerInfo.id -> source
          compilerInfo.compilerService ! req
          // process next in queue
          processQueue()
        case None =>
          // no compiler available
      }
    }
  }

  def receive = {
    case RegisterCompiler(id, compilerService) =>
      compilers += id -> CompilerInfo(id, compilerService, CompilerState.Initializing, now)
      // send current libraries
      compilerService ! UpdateLibraries(currentLibs)
      context.watch(compilerService)

    case UnregisterCompiler(id) =>
      compilers.get(id).foreach(info => context.unwatch(info.compilerService))
      compilers -= id

    case Terminated(compilerService) =>
      // check if it still exist in the map
      compilers.find(_._2.compilerService == compilerService) match {
        case Some((id, info)) =>
          compilers -= id
        case _ =>
      }

    case UpdateState(id, newState) =>
      updateCompilerState(id, newState)

    case req: CompilerRequest =>
      // add to the queue
      compilerQueue.enqueue((req, sender()))
      processQueue()

    case CancelCompilation(id) =>
      compilerQueue = compilerQueue.filterNot(_._1.id == id)

    case (id: String, CompilerReady) =>
      log.debug(s"Compiler $id is now ready")
      updateCompilerState(id, CompilerState.Ready)
      processQueue()

    case (id: String, response: CompilerResponse) =>
      log.debug(s"Received compiler response from $id")
      updateCompilerState(id, CompilerState.Ready)
      compilationPending.get(id) match {
        case Some(actor) =>
          compilationPending -= id
          actor ! Right(response)
        case None =>
          log.error(s"No compilation pending for compiler $id")
      }
      processQueue()
  }
}

object CompilerManager {
  def props = Props(new CompilerManager)

  case class CompilerInfo(id: String, compilerService: ActorRef, state: CompilerState, lastActivity: Long)

}
