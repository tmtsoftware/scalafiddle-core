package fiddle

import com.typesafe.config.ConfigFactory
import spray.http.HttpHeader
import spray.http.HttpHeaders.RawHeader

import scala.collection.JavaConverters._

case class Template(pre: String, post: String) {
  def fullSource(src: String) = pre + src + post
}

object Config {
  protected val config = ConfigFactory.load().getConfig("fiddle")

  val interface = config.getString("interface")
  val port = config.getInt("port")

  val clientFiles = config.getStringList("clientFiles").asScala

  val extLibs = config.getStringList("extLibs").asScala
  val extJS = config.getStringList("extJS").asScala
  val extCSS = config.getStringList("extCSS").asScala

  val libCache = config.getString("libCache")

  val templates = config.getConfigList("templates").asScala.map { co =>
    co.getString("name") -> Template(co.getString("pre"), co.getString("post"))
  }.toMap

  val httpHeaders: List[HttpHeader] = config.getConfig("httpHeaders").entrySet().asScala.map { entry =>
    RawHeader(entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
  }.toList
}

