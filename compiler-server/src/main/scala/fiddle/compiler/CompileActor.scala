package fiddle.compiler

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, PoisonPill, Props}
import akka.http.scaladsl.model.ws.TextMessage
import fiddle.shared._
import org.scalajs.core.tools.io.VirtualScalaJSIRFile
import upickle.default._

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

class CompileActor(out: ActorRef, manager: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  var timer: Cancellable = _
  var libraryManager: LibraryManager = _

  override def preStart(): Unit = {
    log.debug("Compiler actor starting")
    timer = context.system.scheduler.schedule(5.seconds, 15.seconds, context.self, Ping)
    super.preStart()
  }

  def sendOut(msg: CompilerMessage): Unit = {
    out ! TextMessage.Strict(write[CompilerMessage](msg))
  }

  def receive = {
    case Ping =>
      sendOut(Ping)

    case TextMessage.Strict(msg) =>
      read[CompilerMessage](msg) match {
        case UpdateLibraries(extLibs) =>
          log.debug(s"Received ${extLibs.size} libraries")
          // library loading can take some time, run in another thread
          Future(new LibraryManager(extLibs)).map { mgr =>
            libraryManager = mgr
            sendOut(CompilerReady)
          } recover {
            case e =>
              log.error(e, "Error while loading libraries")
              context.self ! PoisonPill
          }

        case CompilationRequest(id, sourceCode, optimizer) =>
          val compiler = new Compiler(libraryManager, sourceCode)
          try {
            val opt = optimizer match {
              case "fast" => compiler.fastOpt _
              case "full" => compiler.fullOpt _
            }
            val res = doCompile(compiler, sourceCode, e => compiler.export(opt(e)))
            sendOut(res)
          } catch {
            case e: Throwable =>
              log.error(s"Error in compilation", e)
              sendOut(CompilationResponse(None, Seq(EditorAnnotation(0, 0, e.getMessage +: compiler.getLog, "ERROR")), compiler.getLog.mkString("\n")))
          }

        case CompletionRequest(id, sourceCode, offset) =>
          val compiler = new Compiler(libraryManager, sourceCode)
          try {
            sendOut(CompletionResponse(compiler.autocomplete(offset.toInt)))
          } catch {
            case e: Throwable =>
              sendOut(CompletionResponse(List.empty))
          }

        case Pong =>
          // no action

        case other =>
          log.error(s"Unsupported compiler message $other")
      }
  }

  val errorStart = """^\w+.scala:(\d+): *(\w+): *(.*)""".r
  val errorEnd = """ *\^ *$""".r

  def parseErrors(log: String): Seq[EditorAnnotation] = {
    val lines = log.split('\n').toSeq.map(_.replaceAll("[\\n\\r]", ""))
    val (annotations, _) = lines.foldLeft((Seq.empty[EditorAnnotation], Option.empty[EditorAnnotation])) { case ((acc, current), line) =>
      line match {
        case errorStart(lineNo, severity, msg) =>
          val ann = EditorAnnotation(lineNo.toInt - 1, 0, Seq(msg), severity)
          (acc, Some(ann))
        case errorEnd() if current.isDefined =>
          val ann = current.map(ann => ann.copy(col = line.length, text = ann.text :+ line)).get
          (acc :+ ann, None)
        case errLine =>
          (acc, current.map(ann => ann.copy(text = ann.text :+ errLine)))
      }
    }
    annotations
  }

  def doCompile(compiler: Compiler, sourceCode: String, processor: Seq[VirtualScalaJSIRFile] => String): CompilationResponse = {
    val output = mutable.Buffer.empty[String]

    val (logSpam, res) = compiler.compile(output.append(_))
    if(logSpam.nonEmpty)
      println(s"Compiler errors: $logSpam")

    CompilationResponse(res.map(processor), parseErrors(logSpam), logSpam)
  }

  override def postStop(): Unit = {
    manager ! CompilerTerminated
    super.postStop()
  }
}

object CompileActor {
  def props(out: ActorRef, manager: ActorRef) = Props(new CompileActor(out, manager))
}