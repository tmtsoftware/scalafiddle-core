package scalafiddle.router

import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import upickle.default._

import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration

object Config {
  protected val config = ConfigFactory.load().getConfig("fiddle")

  // read the generated version data
  protected val versionProps = new Properties()
  versionProps.load(getClass.getResourceAsStream("/version.properties"))

  val interface            = config.getString("interface")
  val port                 = config.getInt("port")
  val analyticsID          = config.getString("analyticsID")
  val secret               = config.getString("secret")
  val scalaFiddleSourceUrl = config.getString("scalaFiddleSourceUrl")
  val scalaFiddleEditUrl   = config.getString("scalaFiddleEditUrl")
  val scalaFiddleEmbedUrl  = config.getString("scalaFiddleEmbedUrl")

  val scalaVersions       = read[Seq[String]](config.getString("scalaVersions"))
  val defaultScalaVersion = config.getString("defaultScalaVersion")
  val defaultLibs         = read[Map[String, Seq[String]]](config.getString("defaultLibs"))
  val extLibs             = read[Map[String, String]](config.getString("extLibs"))
  val refreshLibraries    = FiniteDuration(config.getDuration("refreshLibraries").toMillis, TimeUnit.MILLISECONDS)

  val corsOrigins = config.getStringList("corsOrigins").asScala

  val version    = versionProps.getProperty("version")
  val aceVersion = versionProps.getProperty("aceVersion")

  val logoLight   = config.getString("logoLight")
  val logoLight2x = config.getString("logoLight2x")
  val logoDark    = config.getString("logoDark")
  val logoDark2x  = config.getString("logoDark2x")

  val extJS       = config.getStringList("extJS").asScala
  val extCSS      = config.getStringList("extCSS").asScala
  val baseEnv     = config.getString("baseEnv")
  val clientFiles = config.getStringList("clientFiles").asScala

  val cacheDir = config.getString("cacheDir")
}
