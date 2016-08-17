package fiddle

import java.util.Properties

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import com.typesafe.config.ConfigFactory
import upickle.default._

import scala.collection.JavaConverters._

case class Template(pre: String, post: String) {
  def fullSource(src: String) = pre + src + post
}

object Config {
  def loadLibraries(uri: String): Seq[ExtLib] = {
    val data = if (uri.startsWith("file:")) {
      // load from file system
      scala.io.Source.fromFile(uri.drop(5), "UTF-8").mkString
    } else {
      // load from resources
      scala.io.Source.fromInputStream(getClass.getResourceAsStream(uri), "UTF-8").mkString
    }
    read[Seq[String]](data).map(ExtLib(_))
  }

  protected val config = ConfigFactory.load().getConfig("fiddle")
  // read the generated version data
  protected val versionProps = new Properties()
  versionProps.load(getClass.getResourceAsStream("/version.properties"))

  val interface = config.getString("interface")
  val port = config.getInt("port")
  val analyticsID = config.getString("analyticsID")
  val helpUrl = config.getString("helpUrl")
  val scalaFiddleSourceUrl = config.getString("scalaFiddleSourceUrl")

  val clientFiles = config.getStringList("clientFiles").asScala

  val extLibs = loadLibraries(config.getString("extLibs"))

  val extJS = config.getStringList("extJS").asScala
  val extCSS = config.getStringList("extCSS").asScala

  val libCache = config.getString("libCache")

  val baseEnv = config.getString("baseEnv")

  val httpHeaders: List[HttpHeader] = config.getConfig("httpHeaders").entrySet().asScala.map { entry =>
    RawHeader(entry.getKey, entry.getValue.unwrapped().asInstanceOf[String])
  }.toList

  val corsOrigins = config.getStringList("corsOrigins").asScala

  val version = versionProps.getProperty("version")
  val scalaVersion = versionProps.getProperty("scalaVersion")
  val scalaMainVersion = scalaVersion.split('.').take(2).mkString(".")
  val scalaJSVersion = versionProps.getProperty("scalaJSVersion")
  val scalaJSMainVersion = scalaJSVersion.split('.').take(2).mkString(".")
  val aceVersion = versionProps.getProperty("aceVersion")
}
