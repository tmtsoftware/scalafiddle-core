package fiddle

import java.net.URLDecoder

import autowire._
import fiddle.Client.RedLogger
import fiddle.JsVal.jsVal2jsAny
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.{HTMLElement, MouseEvent, SVGElement}
import upickle._

import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.runNow
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

@JSExport("Checker")
object Checker {
  /**
    * Deadline by which the user code must complete execution.
    */
  private[this] var endTime = 0.0
  /**
    * Switch to flip to once you have run out of time to make
    * `check` fail every single time, ensuring you get thrown out
    * of the user code
    */
  private[this] var dead = false
  /**
    * Used to avoid doing an expensive `currentTimeMillis` check on every call,
    * and instead doing one every N calls.
    */
  private[this] var count = 0
  @JSExport
  def check(): Unit = {
    count += 1
    if (count % 1000 == 0 && js.Date.now() > endTime || dead) {
      dead = true
      Client.clearTimeouts()
      js.eval("""throw new Error("Time's Up! Your code took too long to run.")""")
    }
  }

  @JSExport
  def reset(max: Int) = {
    count = 0
    dead = false
    endTime = math.max(js.Date.now() + max, endTime)
  }

  def scheduleResets() = {
    dom.window.setInterval(() => Checker.reset(1000), 100)
  }
}

object Post extends autowire.Client[String, upickle.Reader, upickle.Writer] {
  override def doCall(req: Request): Future[String] = {
    val url = "/api/" + req.path.mkString("/")
    Ajax.post(
      url = Shared.url + url,
      data = upickle.write(req.args)
    ).map(_.responseText)
  }
  def read[Result: upickle.Reader](p: String) = upickle.read[Result](p)
  def write[Result: upickle.Writer](r: Result) = upickle.write(r)
}

class Client(template: String) {
  var origSrc = ""

  Client.scheduleResets()
  val command = Channel[Future[(String, Seq[EditorAnnotation], Option[String])]]()

  def exec(s: String) = {
    Client.clear()
    Client.scheduleResets()

    Checker.reset(1000)
    try {
      js.eval(s)
      js.eval("ScalaJSExample().main()")
    } catch {
      case e: Throwable =>
        Client.logError(e.getStackTraceString)
        Client.logError(e.toString())
    }
  }
  val instrument = "c"

  val compilationLoop = task * async {
    val future = await(command())
    await(compile(future)).foreach(exec)

    while (true) {
      val future = await(command())

      val compiled = await(compile(future))
      compiled.foreach(exec)
    }
  }

  val editor: Editor = new Editor(Seq(
    ("Compile", "Enter", () => fastOpt),
    ("FullOptimize", "Shift-Enter", () => fullOpt),
    ("Save", "S", save _),
    ("Complete", "Space", () => editor.complete()),
    ("FastOptimizeJavascript", "J", () => showJavascript(Post[Api].fastOpt(template, editor.code).call())),
    ("FullOptimizedJavascript", "Shift-J", () => showJavascript(Post[Api].fullOpt(template, editor.code).call())),
    ("Export", "E", export _)
  ), complete, RedLogger)

  def beginCompilation(): Unit = {
    runIcon.classList.add("active")
    outputTag.innerHTML = "Compiling"
  }

  def endCompilation(): Unit = {
    runIcon.classList.remove("active")
    outputTag.innerHTML = "Output"
    editor.focus()
  }

  def fullOpt = {
    beginCompilation()
    command.update(Post[Api].fullOpt(template, editor.code).call())
  }

  def fastOpt = {
    beginCompilation()
    command.update(Post[Api].fastOpt(template, editor.code).call())
  }

  val landing = fiddle.Shared.url + "/gist/" + fiddle.Shared.gistId + "/LandingPage.scala"

  // attach handlers to icons
  val runIcon: SVGElement = dom.document.getElementById("run-icon").asInstanceOf[SVGElement]
  runIcon.onclick = (e: MouseEvent) => {
    if (e.shiftKey)
      fullOpt
    else
      fastOpt
  }

  val resetIcon: SVGElement = dom.document.getElementById("reset-icon").asInstanceOf[SVGElement]
  resetIcon.onclick = (e: MouseEvent) => {
    editor.sess.setValue(origSrc)
    editor.focus()
  }

  val outputTag: HTMLElement = dom.document.getElementById("output-tag").asInstanceOf[HTMLElement]

  def compile(res: Future[(String, Seq[EditorAnnotation], Option[String])]): Future[Option[String]] = {

    res.map { case (logspam, annotations, result) =>
      endCompilation()
      editor.setAnnotations(annotations)
      result
    }.recover { case e: Exception =>
      endCompilation()
      Client.logError(e.getStackTraceString)
      Client.logError(e.toString)
      None
    }
  }

  def showJavascript(compiled: Future[(String, Seq[EditorAnnotation], Option[String])]) = {
    compiled.collect { case (logspam, annotations, Some(code)) =>
      Client.clear()
      editor.setAnnotations(annotations)
      Page.output.innerHTML = Page.highlight(code, "ace/mode/javascript")
    }
  }

  def complete() = async {
    val code = editor.sess.getValue().asInstanceOf[String]

    val intOffset = editor.column + code.split("\n")
      .take(editor.row)
      .map(_.length + 1)
      .sum

    val flag = if (code.take(intOffset).endsWith(".")) "member" else "scope"


    val res = await(Post[Api].completeStuff(template, code, flag, intOffset).call())
    res
  }

  def export(): Unit = task * async {
    await(compile(Post[Api].fullOpt(template, editor.code).call())).foreach { code =>
      Util.Form.post("/export",
        "source" -> editor.code,
        "compiled" -> code
      )
    }
  }

  def save(): Unit = task * async {
    await(compile(Post[Api].fullOpt(template, editor.code).call()))
    val data = JsVal.obj(
      "description" -> "Scala.jsFiddle gist",
      "public" -> true,
      "files" -> JsVal.obj(
        "Main.scala" -> JsVal.obj(
          "content" -> editor.code
        )
      )
    ).toString()

    val res = await(Ajax.post("https://api.github.com/gists", data = data))
    val result = JsVal.parse(res.responseText)
    Util.Form.get("/gist/" + result("id").asString)
  }
}

@JSExport("Client")
object Client {
  implicit val RedLogger = new Logger(logError)

  dom.window.onerror = { (event: dom.Event, source: String, fileno: Int, columnNumber: Int) =>
    dom.console.log("dom.onerror")
    Client.logError(event.toString())
  }

  def parseUriParameters(search: String): Map[String, String] = {
    search.drop(1).split('&').filter(_.nonEmpty).map { part =>
      val pair = part.split("=")
      val key = URLDecoder.decode(pair(0), "UTF-8")
      val value = if (pair.length > 1) URLDecoder.decode(pair(1), "UTF-8") else ""
      key -> value
    }.toMap
  }

  val queryParams = parseUriParameters(dom.window.location.search)
  val templateId = queryParams.getOrElse("template", "default")

  dom.console.log(s"queryParams: $queryParams, templateId: $templateId")
  @JSExport
  def logError(s: String): Unit = {
    dom.console.error(s)
  }
  @JSExport
  def clearTimeouts() = {
    for (i <- -100000 until 100000) {
      dom.window.clearInterval(i)
      dom.window.clearTimeout(i)
    }
    Client.scheduleResets()
  }
  def clear() = {
    clearTimeouts()
    Page.clear()
  }

  @JSExport
  def gistMain(args: js.Array[String]): Unit = task * async {
    Editor.initEditor
    val (gistId, fileName) = args.toSeq match {
      case Nil => (fiddle.Shared.gistId, Some("Oscilloscope.scala"))
      case Seq(g) => (g, None)
      case Seq(g, f) => (g, Some(f))
    }

    val src = await(load(gistId, fileName))
    val client = new Client(templateId)
    client.editor.sess.setValue(src)

    client.fastOpt
  }

  @JSExport
  def importMain(): Unit = {
    clear()
    val client = new Client(templateId)
  }

  @JSExport
  def main(): Unit = task * async {
    clear()
    Editor.initEditor
    val client = new Client(templateId)
    // is a gist specified?
    if (queryParams.contains("gist")) {
      val (gistId, fileName) = queryParams("gist").span(_ != '/') match {
        case (id, "") => (id, None)
        case (id, name) => (id, Some(name.drop(1)))
      }
      val src = await(load(gistId, fileName))
      client.origSrc = src
      client.editor.sess.setValue(src)
      client.fastOpt
    } else if (queryParams.contains("source")) {
      client.origSrc = queryParams("source")
      client.editor.sess.setValue(client.origSrc)
      client.fastOpt
    }
  }

  val defaultCode = """
                  |import scalajs.js
                  |object ScalaJSExample extends js.JSApp{
                  |  def main() = {
                  |    println("Looks like there was an error loading the default Gist!")
                  |    println("Loading an empty application so you can get started")
                  |  }
                  |}
                """.stripMargin


  def load(gistId: String, file: Option[String]): Future[String] = {
    val gistUrl = "https://gist.github.com/" + gistId
    Ajax.get("https://api.github.com/gists/" + gistId).map { res =>
      val result = JsVal.parse(res.responseText)
      val mainFile = result("files").get(file.getOrElse(""))
      val firstFile = result("files").values(0)
      mainFile.getOrElse(firstFile)("content").asString
    }.recover { case e => defaultCode }
  }
  def scheduleResets() = {
    dom.window.setInterval(() => Checker.reset(1000), 100)
  }
}


