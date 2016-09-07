package fiddle.router

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.CacheDirectives.`max-age`
import akka.http.scaladsl.model.headers.{HttpOrigin, HttpOriginRange, `Cache-Control`}
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import ch.megard.akka.http.cors.{CorsDirectives, CorsSettings}
import fiddle.router.cache.Cache
import fiddle.router.frontend.Static
import org.slf4j.LoggerFactory

import scala.concurrent.Future
import scala.concurrent.duration._

class WebService(system: ActorSystem, router: ActorRef, cache: Cache) {
  implicit val actorSystem = system
  implicit val timeout = Timeout(30.seconds)
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  val log = LoggerFactory.getLogger(getClass)

  val settings = CorsSettings.defaultSettings.copy(allowedOrigins = HttpOriginRange(Config.corsOrigins.map(HttpOrigin(_)): _*))
  import HttpCharsets._
  import MediaTypes._

  type ParamValidator = Map[String, Validator]
  val embedValidator: ParamValidator = Map(
    "responsiveWidth" -> IntValidator(0, 3840),
    "theme" -> ListValidator("dark", "light"),
    "style" -> EmptyValidator,
    "fullOpt" -> EmptyValidator,
    "layout" -> EmptyValidator,
    "hideButtons" -> EmptyValidator
  )
  val codeframeValidator: ParamValidator = Map(
    "theme" -> ListValidator("dark", "light")
  )

  val compileValidator: ParamValidator = Map(
    "source" -> EmptyValidator,
    "opt" -> ListValidator("fast", "full")
  )

  val completeValidator: ParamValidator = Map(
    "source" -> EmptyValidator,
    "offset" -> IntValidator()
  )

  def validateParams(params: Map[String, String], validator: ParamValidator): Option[String] = {
    params.foldLeft(List.empty[Option[String]]) { case (validated, (key, value)) =>
      validator.get(key).flatMap(v => v.isInvalid(value).map(e => s"$key: $e")) :: validated
    }.flatten match {
      case Nil => None
      case errors => Some(errors.mkString("\n"))
    }
  }

  def buildHash(path: String, params: Map[String, String], validator: ParamValidator): String = {
    val sb = new StringBuilder(path)
    params.filterKeys(validator.contains).toSeq.sortBy(_._1).foreach { case (key, value) =>
      sb.append(key).append(value)
    }
    val hash = MessageDigest.getInstance("MD5").digest(sb.toString().getBytes(StandardCharsets.UTF_8))
    hash.map("%02X".format(_)).mkString
  }

  def cacheOr(path: String, params: Map[String, String], validator: ParamValidator, expiration: Int)
    (value: => Future[Either[String, Array[Byte]]])(toResponse: Array[Byte] => HttpResponse): Future[HttpResponse] = {
    validateParams(params, validator) match {
      case Some(error) =>
        Future.successful(HttpResponse(StatusCodes.BadRequest, entity = error))
      case None =>
        // check cache
        val hash = buildHash(path, params, validator)
        cache.get(hash, expiration).flatMap {
          case Some(data) =>
            log.debug(s"Cache hit on $path")
            Future.successful(toResponse(data).withHeaders(`Cache-Control`(`max-age`(expiration))))
          case None =>
            value map {
              case Right(data) =>
                cache.put(hash, data, expiration)
                toResponse(data).withHeaders(`Cache-Control`(`max-age`(expiration)))
              case Left(error) =>
                HttpResponse(StatusCodes.BadRequest, entity = error)
            }
        }
    }
  }

  def forwardRequest(request: HttpRequest): Future[Either[StatusCode, Array[Byte]]] = {
    // filter generated headers away
    val newReq = request.withHeaders(request.headers.filterNot(_.name == "Timeout-Access"))
    ask(router, RouteRequest(newReq)).mapTo[Either[StatusCode, Array[Byte]]]
  }


  val extRoute: Route = {
    encodeResponse {
      get {
        path("embed") {
          parameterMap { paramMap =>
            complete {
              cacheOr("embed", paramMap, embedValidator, 3600)(Future.successful(Right(Static.renderPage(Config.clientFiles, paramMap)))) { data =>
                HttpResponse(entity = HttpEntity(`text/html` withCharset `UTF-8`, data))
              }
            }
          }
        } ~ path("codeframe") {
          parameterMap { paramMap =>
            complete {
              cacheOr("codeframe", paramMap, codeframeValidator, 3600)(Future.successful(Right(Static.renderCodeFrame(paramMap)))) { data =>
                HttpResponse(entity = HttpEntity(`text/html` withCharset `UTF-8`, data))
              }
            }
          }
        } ~ path("compile") {
          handleRejections(CorsDirectives.corsRejectionHandler) {
            CorsDirectives.cors(settings) {
              parameterMap { paramMap =>
                extractRequest { request =>
                  complete {
                    cacheOr("compile", paramMap, compileValidator, 3600 * 24 * 90) {
                      forwardRequest(request).map {
                        case Right(data) =>
                          Right(data)
                        case Left(errorCode) =>
                          Left("Compilation error")
                      }
                    }(data => HttpResponse(entity = HttpEntity(`application/json`, data)))
                  }
                }
              }
            }
          }
        } ~ path("complete") {
          handleRejections(CorsDirectives.corsRejectionHandler) {
            CorsDirectives.cors(settings) {
              parameterMap { paramMap =>
                extractRequest { request =>
                  complete {
                    cacheOr("complete", paramMap, completeValidator, 3600 * 24 * 90) {
                      forwardRequest(request).map {
                        case Right(data) =>
                          Right(data)
                        case Left(errorCode) =>
                          Left("Compilation error")
                      }
                    }(data => HttpResponse(entity = HttpEntity(`application/json`, data)))
                  }
                }
              }
            }
          }
        } ~ path("cache" / Segment) { res =>
          // resources identified by a hash can be cached "forever" (2 years in this case)
          complete {
            val (hash, ext) = res.span(_ != '.')
            val contentType: ContentType = ext match {
              case ".css" => `text/css` withCharset `UTF-8`
              case ".js" => `application/javascript` withCharset `UTF-8`
              case _ => `application/octet-stream`
            }
            cacheOr(s"cache/$res", Map.empty, Map.empty, 3600 * 24 * 365 * 2) {
              Future.successful {
                Static.fetchResource(hash) match {
                  case Some(src) =>
                    Right(src)
                  case None =>
                    Left("")
                }
              }
            }(data => HttpResponse(entity = HttpEntity(contentType, data)))
          }
        } ~ respondWithHeader(`Cache-Control`(`max-age`(3600 * 24))) {
          getFromResourceDirectory("/web")
        }
      }
    }
  }

  def wsFlow: Flow[Message, Message, Any] = ActorFlow.actorRef[Message, Message](out => CompilerService.props(out), () => ())

  val compilerRoute: Route = {
    path("compiler") {
      handleWebSocketMessages(wsFlow)
    }
  }
  val route = extRoute ~ compilerRoute

  val bindingFuture = Http().bindAndHandle(route, Config.interface, Config.port)

  bindingFuture map { binding =>
    log.info(s"Scala Fiddle router ${Config.version} at ${Config.interface}:${Config.port}")
  } recover {
    case ex =>
      log.error(s"Failed to bind to ${Config.interface}:${Config.port}", ex)
  }
}
