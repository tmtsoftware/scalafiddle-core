package fiddle

import com.typesafe.config.ConfigFactory
import scala.collection.JavaConverters._

case class Template(pre: String, post: String) {
  def fullSource(src: String) = pre + src + post
}

object Config {
  protected val config = ConfigFactory.load().getConfig("fiddle")

  val extLibs = config.getStringList("extLibs").asScala

  val libCache = config.getString("libCache")

  val templates = config.getConfigList("templates").asScala.map { co =>
    co.getString("name") -> Template(co.getString("pre"), co.getString("post"))
  }.toMap
}

