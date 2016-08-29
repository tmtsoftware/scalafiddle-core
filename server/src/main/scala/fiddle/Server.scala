package fiddle

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.zip.GZIPInputStream

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives.{`max-age`, `no-cache`}
import akka.http.scaladsl.model.headers.`Cache-Control`
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.{ByteString, Timeout}
import org.slf4j.LoggerFactory
import upickle.default._

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
  val compilerActor = system.actorOf(CompileActor.props(classPath), "compilerActor")

  import MediaTypes._

  val route = {
    get {
      path("compile") {
        parameters('source, 'opt) { (source, opt) =>
          ctx =>
            val optimizer = opt match {
              case "fast" => Optimizer.Fast
              case "full" => Optimizer.Full
              case _ =>
                throw new IllegalArgumentException(s"$opt is not a valid opt value")
            }
            val res = ask(compilerActor, CompileSource(decodeSource(source), optimizer))
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
      } ~ path("complete") {
        parameters('source, 'offset) { (source, offset) =>
          ctx =>
            val res = ask(compilerActor, CompleteSource(decodeSource(source), offset.toInt))
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

  log.info(s"Scala Fiddle compiler ${Config.version} at ${Config.interface}:${Config.port}")

  // start the HTTP server
  val bindingFuture = Http().bindAndHandle(route, Config.interface, Config.port)
}
