package scalafiddle.shared

sealed trait CompilerMessage

case object CompilerReady extends CompilerMessage

case object Ping extends CompilerMessage

case object Pong extends CompilerMessage

case class UpdateLibraries(libs: Seq[ExtLib]) extends CompilerMessage

sealed abstract class CompilerRequest {
  def id: String
  def source: String
  def clientAddress: String
  def updated(f: String => String): CompilerRequest
}

case class CompilationRequest(id: String, source: String, clientAddress: String, opt: String)
    extends CompilerRequest
    with CompilerMessage {
  def updated(f: String => String) = copy(source = f(source))
}

case class CompletionRequest(id: String, source: String, clientAddress: String, offset: Int)
  extends CompilerRequest
    with CompilerMessage {
  def updated(f: String => String) = copy(source = f(source))
}

trait CompilerResponse

case class EditorAnnotation(row: Int, col: Int, text: Seq[String], tpe: String)

case class CompilationResponse(jsCode: Option[String], annotations: Seq[EditorAnnotation], log: String)
    extends CompilerResponse
    with CompilerMessage

case class CompletionResponse(completions: List[(String, String)]) extends CompilerResponse with CompilerMessage
