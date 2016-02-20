package fiddle

case class EditorAnnotation(row: Int, col: Int, text: Seq[String], tpe: String)

case class CompilerResponse(jsCode: Option[String], annotations: Seq[EditorAnnotation], log: String)

trait Api{
  def completeStuff(template: String, txt: String, flag: String, offset: Int): List[(String, String)]
}
