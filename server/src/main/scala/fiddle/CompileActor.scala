package fiddle

import akka.actor.{Actor, Props}
import org.scalajs.core.tools.io.VirtualScalaJSIRFile

import scala.collection.mutable
import scala.util.Try

sealed abstract class Optimizer

object Optimizer {
  case object Fast extends Optimizer

  case object Full extends Optimizer
}

case class CompileSource(sourceCode: String, optimizer: Optimizer)

case class CompleteSource(sourceCode: String, offset: Int)

class CompileActor(classPath: Classpath) extends Actor {
  def receive = {
    case CompileSource(sourceCode, optimizer) =>
      val compiler = new Compiler(classPath, sourceCode)
      val opt = optimizer match {
        case Optimizer.Fast => compiler.fastOpt _
        case Optimizer.Full => compiler.fullOpt _
      }
      try {
        sender() ! doCompile(compiler, sourceCode, e => compiler.export(opt(e)))
      } catch {
        case e: Throwable =>
          sender() ! CompilerResponse(None, Seq(EditorAnnotation(0, 0, e.getMessage +: compiler.getLog, "ERROR")), compiler.getLog.mkString("\n"))
      }

    case CompleteSource(sourceCode, offset) =>
      val compiler = new Compiler(classPath, sourceCode)
      sender() ! Try(compiler.autocomplete(offset.toInt))
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

  def doCompile(compiler: Compiler, sourceCode: String, processor: Seq[VirtualScalaJSIRFile] => String): CompilerResponse = {
    val output = mutable.Buffer.empty[String]

    val res = compiler.compile(output.append(_))
    if(output.nonEmpty)
      println(s"Compiler errors: $output")

    val logSpam = output.mkString
    CompilerResponse(res.map(processor), parseErrors(logSpam), logSpam)
  }
}

object CompileActor {
  def props(classPath: Classpath) = Props(new CompileActor(classPath))
}