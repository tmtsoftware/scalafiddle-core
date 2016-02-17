package fiddle

case class EditorAnnotation(row: Int, col: Int, text: Seq[String], tpe: String)

object Shared{
  val gistId = "9443f8e0ecc68d1058ad"

  //val url = "http://www.scala-js-fiddle.com"
  val url = ""
}

trait Api{
  def fastOpt(template: String, txt: String): (String, Seq[EditorAnnotation], Option[String])
  def fullOpt(template: String, txt: String): (String, Seq[EditorAnnotation], Option[String])
  def export(compiled: String, source: String): String
  def `import`(compiled: String, source: String): String
  def completeStuff(template: String, txt: String, flag: String, offset: Int): List[(String, String)]
}
