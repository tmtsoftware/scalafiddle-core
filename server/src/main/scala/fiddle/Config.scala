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
  def loadLibraries(uri: String, defaultLibs: Seq[String]): Seq[ExtLib] = {
    val data = if (uri.startsWith("file:")) {
      // load from file system
      scala.io.Source.fromFile(uri.drop(5), "UTF-8").mkString
    } else if (uri.startsWith("http")) {
      // load from internet
      System.setProperty("http.agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/28.0.1500.29 Safari/537.36")
      scala.io.Source.fromURL(uri, "UTF-8").mkString
    } else {
      // load from resources
      scala.io.Source.fromInputStream(getClass.getResourceAsStream(uri), "UTF-8").mkString
    }
    val extLibs = read[Seq[String]](data)
    (extLibs ++ defaultLibs).map(ExtLib(_))
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
  val scalaFiddleEditUrl = config.getString("scalaFiddleEditUrl")

  val clientFiles = config.getStringList("clientFiles").asScala

  val defaultLibs = config.getStringList("defaultLibs").asScala

  val extJS = config.getStringList("extJS").asScala
  val extCSS = config.getStringList("extCSS").asScala

  val libCache = config.getString("libCache")

  val baseEnv = config.getString("baseEnv")

  val logoLight = config.getString("logoLight")
  val logoDark = config.getString("logoDark")

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

  val extLibs = loadLibraries(config.getString("extLibs"), defaultLibs)
}
