package fiddle

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.GZIPInputStream

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives.{`max-age`, `no-cache`}
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange, `Cache-Control`}
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.routing.FromConfig
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import org.slf4j.LoggerFactory
import upickle.default._
import ch.megard.akka.http.cors.{CorsSettings, CorsDirectives}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.scalajs.niocharset.StandardCharsets
import scala.util.{Failure, Success, Try}

object Server extends App {
  implicit val system = ActorSystem()
  implicit val timeout = Timeout(30.seconds)
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val log = LoggerFactory.getLogger(getClass)
  // initialize classpath singleton, loads all libraries
  val classPath = new Classpath

  // create compiler router
  val compilerRouter = system.actorOf(FromConfig.props(CompileActor.props(classPath)), "compilerRouter")

  val settings = CorsSettings.defaultSettings.copy(allowedOrigins = HttpOriginRange(Config.corsOrigins.map(HttpOrigin(_)): _*))
  import HttpCharsets._
  import MediaTypes._

  val route = {
    encodeResponse {
      get {
        path("embed") {
          respondWithHeaders(Config.httpHeaders) {
            // main embedded page can be cached for some time (1h for now)
            respondWithHeader(`Cache-Control`(`max-age`(60L * 60L * 1L))) {
              parameterMap { paramMap =>
                complete {
                  HttpEntity(
                    `text/html` withCharset `UTF-8`,
                    Static.renderPage(
                      Config.clientFiles,
                      paramMap
                    )
                  )
                }
              }
            }
          }
        } ~ path("codeframe") {
          respondWithHeaders(Config.httpHeaders) {
            // code frame can be cached for a long time (7d for now)
            respondWithHeader(`Cache-Control`(`max-age`(7 * 60L * 60L * 24L))) {
              parameterMap { paramMap =>
                complete {
                  HttpEntity(
                    `text/html` withCharset `UTF-8`,
                    Static.renderCodeFrame(
                      paramMap
                    )
                  )
                }
              }
            }
          }
        } ~ path("compile") {
          handleRejections(CorsDirectives.corsRejectionHandler) {
            CorsDirectives.cors(settings) {
              parameters('source, 'opt) { (source, opt) =>
                ctx =>
                  val optimizer = opt match {
                    case "fast" => Optimizer.Fast
                    case "full" => Optimizer.Full
                    case _ =>
                      throw new IllegalArgumentException(s"$opt is not a valid opt value")
                  }
                  val res = ask(compilerRouter, CompileSource(decodeSource(source), optimizer))
                    .mapTo[CompilerResponse]
                    .map { cr =>
                      val result = write(cr)
                      // compile results can be cached for a long time (week for now)
                      HttpResponse(StatusCodes.OK, entity = HttpEntity(`application/json`, ByteString(result)), headers = List(`Cache-Control`(`max-age`(7 * 60L * 60L * 24L))))
                    } recover {
                    case e: Exception =>
                      log.error("Error in compilation", e)
                      HttpResponse(StatusCodes.InternalServerError, headers = List(`Cache-Control`(`no-cache`)))
                  }
                  ctx.complete(res)
              }
            }
          }
        } ~ path("complete") {
          handleRejections(CorsDirectives.corsRejectionHandler) {
            CorsDirectives.cors(settings) {
              parameters('source, 'flag, 'offset) { (source, flag, offset) =>
                ctx =>
                  val res = ask(compilerRouter, CompleteSource(decodeSource(source), flag, offset.toInt))
                    .mapTo[Try[List[(String, String)]]]
                    .map {
                      case Success(cr) =>
                        val result = write(cr)
                        // code complete results can be cached for a long time (week for now)
                        HttpResponse(StatusCodes.OK, entity = HttpEntity(`application/json`, result), headers = List(`Cache-Control`(`max-age`(7 * 60L * 60L * 24L))))
                      case Failure(ex) =>
                        log.error("Error in tab completion", ex)
                        HttpResponse(StatusCodes.BadRequest, entity = ex.getMessage.take(64), headers = List(`Cache-Control`(`no-cache`)))
                    }
                  ctx.complete(res)
              }
            }
          }
        } ~ path("cache" / Segment) { res =>
          // resources identified by a hash can be cached "forever" (a year in this case)
          respondWithHeader(`Cache-Control`(`max-age`(60L * 60L * 24L * 365))) {
            complete {
              val (hash, ext) = res.span(_ != '.')
              val contentType: ContentType = ext match {
                case ".css" => `text/css` withCharset `UTF-8`
                case ".js" => `application/javascript` withCharset `UTF-8`
                case _ => `application/octet-stream`
              }
              Static.fetchResource(hash) match {
                case Some(src) =>
                  HttpResponse(StatusCodes.OK, entity = HttpEntity(contentType, src))
                case None =>
                  HttpResponse(StatusCodes.NotFound)
              }
            }
          }
        } ~ getFromResourceDirectory("/web")
      }
    }
  }

  def decodeSource(b64: String): String = {
    import com.github.marklister.base64.Base64._
    implicit def scheme: B64Scheme = base64Url
    // decode base64 and gzip
    val compressedSource = Decoder(b64).toByteArray
    val bis = new ByteArrayInputStream(compressedSource)
    val zis = new GZIPInputStream(bis)
    val buf = new Array[Byte](1024)
    val bos = new ByteArrayOutputStream()
    var len = 0
    while ( {len = zis.read(buf); len > 0}) {
      bos.write(buf, 0, len)
    }
    zis.close()
    bos.close()
    val source = bos.toByteArray
    new String(source, StandardCharsets.UTF_8)
  }

  println(s"Scala Fiddle ${Config.version}")

  // start the HTTP server
  val bindingFuture = Http().bindAndHandle(route, Config.interface, Config.port)
}
