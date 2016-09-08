package fiddle.shared

sealed trait CompilerMessage

case object CompilerReady extends CompilerMessage

case object Ping extends CompilerMessage

case object Pong extends CompilerMessage

case class UpdateLibraries(libs: Seq[ExtLib]) extends CompilerMessage

trait CompilerRequest {
  def id: String
  def source: String
}

case class CompilationRequest(id: String, source: String, opt: String) extends CompilerMessage with CompilerRequest

case class CompletionRequest(id: String, source: String, offset: Int) extends CompilerMessage with CompilerRequest

trait CompilerResponse

case class EditorAnnotation(row: Int, col: Int, text: Seq[String], tpe: String)

case class CompilationResponse(jsCode: Option[String], annotations: Seq[EditorAnnotation], log: String) extends CompilerResponse with CompilerMessage

case class CompletionResponse(completions: List[(String, String)]) extends CompilerResponse with CompilerMessage
