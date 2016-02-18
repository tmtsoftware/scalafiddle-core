package fiddle

import akka.actor.ActorSystem
import org.scalajs.core.tools.io.VirtualScalaJSIRFile
import spray.http.{HttpRequest, HttpResponse, _}
import spray.httpx.encoding.Gzip
import spray.routing._
import spray.routing.directives.CacheKeyer
import spray.routing.directives.CachingDirectives._
import upickle._

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Properties

object Server extends SimpleRoutingApp with Api {
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
              complete {
                HttpEntity(
                  MediaTypes.`text/html`,
                  Static.page(
                    s"Client().main()",
                    clientFiles,
                    "Loading gist..."
                  )
                )
              }
            } ~ path("compile") {
              parameters('source, 'opt, 'template ?) { (source, opt, template) =>
                val res = opt match {
                  case "fast" =>
                    val result = upickle.write(fastOpt(template.getOrElse("default"), source))
                    HttpResponse(StatusCodes.OK, HttpEntity(MediaTypes.`application/json`, result))
                  case "full" =>
                    val result = upickle.write(fullOpt(template.getOrElse("default"), source))
                    HttpResponse(StatusCodes.OK, HttpEntity(MediaTypes.`application/json`, result))
                  case _ =>
                    HttpResponse(StatusCodes.BadRequest)
                }
                complete(res)
              }
            } ~
              getFromResourceDirectory("")
          } ~
            post {
              path("api" / Segments) { s =>
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
  def fastOpt(template: String, txt: String) = compileStuff(template, txt, _ |> Compiler.fastOpt |> Compiler.export)
  def fullOpt(template: String, txt: String) = compileStuff(template, txt, _ |> Compiler.fullOpt |> Compiler.export)
  def export(compiled: String, source: String) = {
    renderCode(compiled, Nil, source, "Page().exportMain(); ScalaFiddle().main();", analytics = false)
  }
  def `import`(compiled: String, source: String) = {
    renderCode(compiled, clientFiles, source, "Client().importMain(); ScalaFiddle().main();", analytics = true)
  }
  def renderCode(compiled: String, srcFiles: Seq[String], source: String, bootFunc: String, analytics: Boolean) = {
    Static.page(bootFunc, srcFiles, source, compiled, analytics)
  }

  def completeStuff(template: String, txt: String, flag: String, offset: Int): List[(String, String)] = {
    Await.result(Compiler.autocomplete(template, txt, flag, offset), 100.seconds)
  }

  val errorStart = """^Main.scala:(\d+): (\w+):(.*)""".r
  val errorEnd = """ *\^ *$""".r
  def parseErrors(preRows: Int, log: String): Seq[EditorAnnotation] = {
    val lines = log.split('\n').toSeq.map(_.replaceAll("[\\n\\r]", ""))
    val (annotations, _) = lines.foldLeft((Seq.empty[EditorAnnotation], Option.empty[EditorAnnotation])) { case ((acc, current), line) =>
      line match {
        case errorStart(lineNo, severity, msg) =>
          val ann = EditorAnnotation(lineNo.toInt - preRows - 1, 0, Seq(msg), severity)
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


  def compileStuff(templateId: String, code: String, processor: Seq[VirtualScalaJSIRFile] => String) = {
    println(s"Using template $templateId")
    val output = mutable.Buffer.empty[String]

    val res = Compiler.compile(templateId, code, output.append(_))
    val template = Compiler.getTemplate(templateId)

    val preRows = template.pre.count(_ == '\n')
    val logSpam = output.mkString
    (logSpam, parseErrors(preRows, logSpam), res.map(processor))
  }
}
