package fiddle

import java.util.{Properties, ResourceBundle}

import com.typesafe.config.ConfigFactory
import spray.http.HttpHeader
import spray.http.HttpHeaders.RawHeader

import scala.collection.JavaConverters._

case class Template(pre: String, post: String) {
  def fullSource(src: String) = pre + src + post
}

object Config {
  protected val config = ConfigFactory.load().getConfig("fiddle")
  // read the generated version data
  protected val versionProps = new Properties()
  versionProps.load(getClass.getResourceAsStream("/version.properties"))

  val interface = config.getString("interface")
  val port = config.getInt("port")
  val analyticsID = config.getString("analyticsID")

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

  val version = versionProps.getProperty("version")
  val scalaVersion = versionProps.getProperty("scalaVersion")
  val scalaMainVersion = scalaVersion.split('.').take(2).mkString(".")
  val scalaJSVersion = versionProps.getProperty("scalaJSVersion")
  val scalaJSMainVersion = scalaJSVersion.split('.').take(2).mkString(".")
  val aceVersion = versionProps.getProperty("aceVersion")
}

