package fiddle

case class EditorAnnotation(row: Int, col: Int, text: Seq[String], tpe: String)

object Shared{
  val prelude =
    """
      |import scalatags.JsDom.all._
      |import org.scalajs.dom
      |import fiddle.Page
      |import Page.{red, green, blue, yellow, orange, println}
      |import scalajs.js
    """.stripMargin

  val default = """
    |import scalajs.js
    |object ScalaJSExample extends js.JSApp{
    |  def main() = {
    |    println("Looks like there was an error loading the default Gist!")
    |    println("Loading an empty application so you can get started")
    |  }
    |}
  """.stripMargin

  val gistId = "9443f8e0ecc68d1058ad"

  //val url = "http://www.scala-js-fiddle.com"
  val url = ""
}

trait Api{
  def fastOpt(txt: String): (String, Seq[EditorAnnotation], Option[String])
  def fullOpt(txt: String): (String, Seq[EditorAnnotation], Option[String])
  def export(compiled: String, source: String): String
  def `import`(compiled: String, source: String): String
  def completeStuff(txt: String, flag: String, offset: Int): List[(String, String)]
}
