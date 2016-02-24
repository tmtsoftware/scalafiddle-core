package fiddle

import akka.actor.ActorSystem
import fiddle.Base64.B64Scheme
import org.scalajs.core.tools.io.VirtualScalaJSIRFile
import spray.http.CacheDirectives.`max-age`
import spray.http.HttpHeaders.`Cache-Control`
import spray.http._
import spray.httpx.encoding.Gzip
import spray.routing._
import upickle.default._

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.scalajs.niocharset.StandardCharsets

object Server extends SimpleRoutingApp with Api {
  implicit val system = ActorSystem()
  import system.dispatcher

  def main(args: Array[String]): Unit = {

    startServer(Config.interface, Config.port) {
      encodeResponse(Gzip) {
        get {
          path("embed") {
            respondWithHeaders(Config.httpHeaders) {
              parameterMap { paramMap =>
                complete {
                  HttpEntity(
                    MediaTypes.`text/html`,
                    Static.page(
                      s"Client().main()",
                      Config.clientFiles,
                      paramMap
                    )
                  )
                }
              }
            }
          } ~ path("compile") {
            parameters('source, 'opt, 'template ?) { (source, opt, template) =>
              val res = opt match {
                case "fast" =>
                  val result = write(fastOpt(template.getOrElse("default"), decodeSource(source)))
                  HttpResponse(StatusCodes.OK, HttpEntity(MediaTypes.`application/json`, result))
                case "full" =>
                  val result = write(fullOpt(template.getOrElse("default"), decodeSource(source)))
                  HttpResponse(StatusCodes.OK, HttpEntity(MediaTypes.`application/json`, result))
                case _ =>
                  HttpResponse(StatusCodes.BadRequest)
              }
              complete(res)
            }
          } ~ path("complete") {
            parameters('source, 'flag, 'offset, 'template ?) { (source, flag, offset, template) =>
              complete {
                val result = write(Await.result(Compiler.autocomplete(template.getOrElse("default"), decodeSource(source), flag, offset.toInt), 100.seconds))
                HttpResponse(StatusCodes.OK, HttpEntity(MediaTypes.`application/json`, result))
              }
            }
          } ~ path("cache" / Segment) { res =>
            respondWithHeader(`Cache-Control`(`max-age`(60L * 60L * 24L * 365))) {
              complete {
                val (hash, ext) = res.span(_ != '.')
                val contentType = ext match {
                  case ".css" => MediaTypes.`text/css`
                  case ".js" => MediaTypes.`application/javascript`
                  case _ => MediaTypes.`application/octet-stream`
                }
                Static.fetchResource(hash) match {
                  case Some(src) =>
                    HttpResponse(StatusCodes.OK, HttpEntity(contentType, src))
                  case None =>
                    HttpResponse(StatusCodes.NotFound)
                }
              }
            }
          } ~ getFromResourceDirectory("")
        }
      }
    }
  }

  def decodeSource(b64: String): String = {
    implicit def scheme: B64Scheme = Base64.base64Url
    new String(Base64.Decoder(b64).toByteArray, StandardCharsets.UTF_8)
  }

  def fastOpt(template: String, txt: String) = compileStuff(template, txt, _ |> Compiler.fastOpt |> Compiler.export)

  def fullOpt(template: String, txt: String) = compileStuff(template, txt, _ |> Compiler.fullOpt |> Compiler.export)

  def completeStuff(template: String, txt: String, flag: String, offset: Int): List[(String, String)] = {
    Await.result(Compiler.autocomplete(template, txt, flag, offset), 100.seconds)
  }

  val errorStart = """^Main.scala:(\d+): *(\w+): *(.*)""".r
  val errorEnd = """ *\^ *$""".r

  def parseErrors(preRows: Int, log: String): Seq[EditorAnnotation] = {
    val lines = log.split('\n').toSeq.map(_.replaceAll("[\\n\\r]", ""))
    val (annotations, _) = lines.foldLeft((Seq.empty[EditorAnnotation], Option.empty[EditorAnnotation])) { case ((acc, current), line) =>
      line match {
        case errorStart(lineNo, severity, msg) =>
          val ann = EditorAnnotation(lineNo.toInt - preRows - 1, 0, Seq(msg), severity)
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


  def compileStuff(templateId: String, code: String,
    processor: Seq[VirtualScalaJSIRFile] => String): CompilerResponse = {
    println(s"Using template $templateId")
    val output = mutable.Buffer.empty[String]

    val res = Compiler.compile(templateId, code, output.append(_))
    val template = Compiler.getTemplate(templateId)

    val preRows = template.pre.count(_ == '\n')
    val logSpam = output.mkString
    CompilerResponse(res.map(processor), parseErrors(preRows, logSpam), logSpam)
  }
}
