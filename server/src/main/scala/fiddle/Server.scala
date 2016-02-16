package fiddle
import acyclic.file
import spray.http._
import spray.http.HttpHeaders._
import spray.httpx.encoding.Gzip
import spray.routing.directives.CachingDirectives._
import akka.actor.ActorSystem
import spray.routing.directives.CacheKeyer
import scala.collection.mutable
import spray.client.pipelining._

import spray.http.HttpRequest
import scala.Some
import spray.http.HttpResponse
import spray.routing._
import upickle._
import org.scalajs.core.tools.io.VirtualScalaJSIRFile
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties

object Server extends SimpleRoutingApp with Api{
  implicit val system = ActorSystem()
  import system.dispatcher
  val clientFiles = Seq("/client-fastopt.js")

  private object AutowireServer
      extends autowire.Server[String, upickle.Reader, upickle.Writer] {
    def write[Result: Writer](r: Result) = upickle.write(r)
    def read[Result: Reader](p: String) = upickle.read[Result](p)

    val routes = AutowireServer.route[Api](Server)
  }

  def main(args: Array[String]): Unit = {
    implicit val Default: CacheKeyer = CacheKeyer {
      case RequestContext(HttpRequest(_, uri, _, entity, _), _, _) => (uri, entity)
    }

    val simpleCache = routeCache()
//    println("Power On Self Test")
//    val res = Compiler.compile(fiddle.Shared.default.getBytes, println)
//    val optimized = res.get |> Compiler.fullOpt |> Compiler.export
//    assert(optimized.contains("Looks like"))
//    println("Power On Self Test complete: " + optimized.length + " bytes")

    val p = Properties.envOrElse("PORT", "8080").toInt
    startServer("0.0.0.0", port = p) {
      cache(simpleCache) {
        encodeResponse(Gzip) {
          get {
            pathSingleSlash {
              complete{
                HttpEntity(
                  MediaTypes.`text/html`,
                  Static.page(
                    s"Client().gistMain([])",
                    clientFiles,
                    "Loading gist..."
                  )
                )
              }
            } ~
            path("gist" / Segments){ i =>
              complete{
                HttpEntity(
                  MediaTypes.`text/html`,
                  Static.page(
                    s"Client().gistMain(${write(i)})",
                    clientFiles,
                    "Loading gist..."
                  )
                )
              }
            } ~
            getFromResourceDirectory("")
          } ~
          post {
            path("api" / Segments){ s =>
              extract(_.request.entity.asString) { e =>
                complete {
                  AutowireServer.routes(
                    autowire.Core.Request(s, upickle.read[Map[String, String]](e))
                  )
                }
              }
            }
          }
        }
      }
    }
  }
  def fastOpt(txt: String) = compileStuff(txt, _ |> Compiler.fastOpt |> Compiler.export)
  def fullOpt(txt: String) = compileStuff(txt, _ |> Compiler.fullOpt |> Compiler.export)
  def export(compiled: String, source: String) = {
    renderCode(compiled, Nil, source, "Page().exportMain(); ScalaJSExample().main();", analytics = false)
  }
  def `import`(compiled: String, source: String) = {
    renderCode(compiled, clientFiles, source, "Client().importMain(); ScalaJSExample().main();", analytics = true)
  }
  def renderCode(compiled: String, srcFiles: Seq[String], source: String, bootFunc: String, analytics: Boolean) = {
    Static.page(bootFunc, srcFiles, source, compiled, analytics)
  }

  def completeStuff(txt: String, flag: String, offset: Int): List[(String, String)] = {
    Await.result(Compiler.autocomplete(txt, flag, offset), 100.seconds)
  }

  val errorStart = """^Main.scala:(\d+): (\w+):(.*)""".r
  val errorEnd = """ *\^ *$""".r
  def parseErrors(log: String): Seq[EditorAnnotation] = {
    val deltaRow = Shared.prelude.count(_ == '\n')
    val lines = log.split('\n').toSeq.map(_.replaceAll("[\\n\\r]", ""))
    val (annotations, _) = lines.foldLeft((Seq.empty[EditorAnnotation], Option.empty[EditorAnnotation])) { case ((acc, current), line) =>
      line match {
        case errorStart(lineNo, severity, msg) =>
          val ann = EditorAnnotation(lineNo.toInt - deltaRow - 1, 0, Seq(msg), severity)
          (acc, Some(ann))
        case errorEnd() if current.isDefined =>
          // drop last line from error message, it's the line of code
          val ann = current.map(ann => ann.copy(col = line.length, text = ann.text.init)).get
          (acc :+ ann, None)
        case errLine if current.isDefined =>
          (acc, current.map(ann => ann.copy(text = ann.text :+ errLine)))
        case _ =>
          (acc, current)
      }
    }
    println(s"Errors: $annotations")
    annotations
  }


  def compileStuff(code: String, processor: Seq[VirtualScalaJSIRFile] => String) = {

    val output = mutable.Buffer.empty[String]

    val res = Compiler.compile(
      code.getBytes,
      output.append(_)
    )

    val logSpam = output.mkString
    (logSpam, parseErrors(logSpam), res.map(processor))
  }
}
