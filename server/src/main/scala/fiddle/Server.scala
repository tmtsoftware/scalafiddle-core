package fiddle

import akka.actor.{ActorSystem, Props}
import akka.pattern.ask
import akka.routing.FromConfig
import akka.util.Timeout
import fiddle.Base64.B64Scheme
import spray.http.CacheDirectives.`max-age`
import spray.http.HttpHeaders.`Cache-Control`
import spray.http._
import spray.httpx.encoding.Gzip
import spray.routing._
import upickle.default._

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.scalajs.niocharset.StandardCharsets

object Server extends SimpleRoutingApp {
  implicit val system = ActorSystem()
  implicit val timeout = Timeout(30.seconds)
  import system.dispatcher

  // create compiler router
  val compilerRouter = system.actorOf(FromConfig.props(Props[CompileActor]), "compilerRouter")

  println(s"Scala Fiddle ${Config.version}")
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
                      Config.clientFiles,
                      paramMap
                    )
                  )
                }
              }
            }
          } ~ path("compile") {
            parameters('source, 'opt, 'template ?) { (source, opt, template) =>
              ctx =>
                val optimizer = opt match {
                  case "fast" => Optimizer.Fast
                  case "full" => Optimizer.Full
                  case _ =>
                    throw new IllegalArgumentException(s"$opt is not a valid opt value")
                }
                val res = ask(compilerRouter, CompileSource(template.getOrElse("default"), decodeSource(source), optimizer))
                  .mapTo[CompilerResponse]
                  .map { cr =>
                    val result = write(cr)
                    HttpResponse(StatusCodes.OK, HttpEntity(MediaTypes.`application/json`, result))
                  } recover {
                  case e: Exception =>
                    HttpResponse(StatusCodes.InternalServerError)
                }
                ctx.complete(res)
            }
          } ~ path("complete") {
            parameters('source, 'flag, 'offset, 'template ?) { (source, flag, offset, template) =>
              ctx =>
                val res = ask(compilerRouter, CompleteSource(template.getOrElse("default"), decodeSource(source), flag, offset.toInt))
                  .mapTo[List[(String, String)]]
                  .map { cr =>
                    val result = write(cr)
                    HttpResponse(StatusCodes.OK, HttpEntity(MediaTypes.`application/json`, result))
                  }
                ctx.complete(res)
            }
          } ~ path("cache" / Segment) { res =>
            // resources identified by a hash can be cached "forever" (a year in this case)
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
}
