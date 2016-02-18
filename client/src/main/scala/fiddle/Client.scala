package fiddle

import java.net.URLDecoder

import autowire._
import fiddle.Client.RedLogger
import fiddle.JsVal.jsVal2jsAny
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw._
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

case class SourceFile(name: String, var code: String)

class Client(template: String) {
  var sourceFiles = Seq(SourceFile("ScalaFiddle.scala", ""))
  var currentSource = sourceFiles.head.name

  Client.scheduleResets()
  val command = Channel[Future[(String, Seq[EditorAnnotation], Option[String])]]()

  def exec(s: String) = {
    Client.clear()
    Client.scheduleResets()

    Checker.reset(1000)
    try {
      js.eval(s)
      js.eval("ScalaFiddle().main()")
    } catch {
      case e: Throwable =>
        Client.logError(e.getStackTraceString)
        Client.logError(e.toString())
    }
  }

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
    ("Complete", "Space", () => editor.complete())
  ), complete, RedLogger)

  def beginCompilation(): Unit = {
    runIcon.classList.add("active")
    showStatus("Compiling")
  }

  def endCompilation(): Unit = {
    runIcon.classList.remove("active")
    showStatus("Output")
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

  val runIcon: SVGElement = dom.document.getElementById("run-icon").asInstanceOf[SVGElement]
  val resetIcon: SVGElement = dom.document.getElementById("reset-icon").asInstanceOf[SVGElement]
  val outputTag: HTMLElement = dom.document.getElementById("output-tag").asInstanceOf[HTMLElement]
  val fiddleSelectorDiv: HTMLElement = dom.document.getElementById("fiddleSelectorDiv").asInstanceOf[HTMLElement]
  val fiddleSelector: HTMLSelectElement = dom.document.getElementById("fiddleSelector").asInstanceOf[HTMLSelectElement]

  // attach handlers to icons
  runIcon.onclick = (e: MouseEvent) => {
    if (e.shiftKey)
      fullOpt
    else
      fastOpt
  }

  resetIcon.onclick = (e: MouseEvent) => {
    selectSource(currentSource)
    editor.focus()
  }

  fiddleSelector.onchange = (e: Event) => {
    val sel = fiddleSelector.options(fiddleSelector.selectedIndex).text
    dom.console.log(s"Fiddle selected $sel")
    updateSource(editor.code)
    selectSource(sel)
  }

  def showStatus(status: String) =
    outputTag.innerHTML = status

  def setSources(sources: Seq[SourceFile]): Unit = {
    import scalatags.JsDom.all._

    sourceFiles = sources
    fiddleSelector.innerHTML = ""
    sourceFiles.foreach { source =>
      fiddleSelector.add(option(value := source.name)(source.name).render)
    }
    if (sourceFiles.size > 1) {
      fiddleSelectorDiv.style.display = "block"
    } else {
      fiddleSelectorDiv.style.display = "none"
    }
    selectSource(sourceFiles.head.name)
  }

  def selectSource(name: String): Unit = {
    sourceFiles.find(_.name == name).foreach { src =>
      currentSource = src.name
      editor.sess.setValue(src.code)
    }
  }

  def updateSource(code: String): Unit = {
    sourceFiles = sourceFiles.collect {
      case SourceFile(name, _) if name == currentSource => SourceFile(name, code)
      case sf => sf
    }
  }

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

  def save(): Unit = task * async {
    await(compile(Post[Api].fullOpt(template, editor.code).call()))
    val data = JsVal.obj(
      "description" -> "ScalaFiddle gist",
      "public" -> true,
      "files" -> JsVal.obj(
        "ScalaFiddle.scala" -> JsVal.obj(
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

  val subFiddleRE = """// \$SubFiddle (\w.+)""".r
  def parseFiddles(srcCode: String): Seq[SourceFile] = {
    val lines = srcCode.split('\n').toSeq.map(_.replaceAll("[\\n\\r]", ""))
    val (prevSources, lastSource) = lines.foldLeft((List.empty[SourceFile], SourceFile("ScalaFiddle.scala", ""))) { case ((acc, src), line) =>
      line match {
        case subFiddleRE(name) =>
          val newSrc = SourceFile(name.trim, "")
          (src :: acc, newSrc)
        case _ =>
          (acc, src.copy(code = src.code + line + "\n"))
      }
    }
    (lastSource :: prevSources).filter(_.code.replaceAll("\\s", "").nonEmpty).reverse
  }

  @JSExport
  def main(): Unit = task * async {
    clear()
    Editor.initEditor
    val client = new Client(templateId)
    // is a gist specified?
    if (queryParams.contains("gist")) {
      val gistId = queryParams("gist")
      val files = queryParams.get("files").map(_.split(',').toList.filter(_.nonEmpty)).getOrElse(Nil)
      client.showStatus("Loading")
      val sources = await(load(gistId, files))
      client.setSources(sources)
      client.fastOpt
    } else if (queryParams.contains("source")) {
      val srcCode = queryParams("source")
      // check for sub-fiddles
      val sources = parseFiddles(srcCode)
      client.setSources(sources)
      client.fastOpt
    }
  }

  val defaultCode =
    """
      |import scalajs.js
      |object ScalaFiddle extends js.JSApp {
      |  def main() = {
      |    println("Looks like there was an error loading the default Gist!")
      |    println("Loading an empty application so you can get started")
      |  }
      |}
    """.stripMargin

  def load(gistId: String, files: Seq[String]): Future[Seq[SourceFile]] = {
    val gistUrl = "https://gist.github.com/" + gistId
    Ajax.get("https://api.github.com/gists/" + gistId).map { res =>
      val result = JsVal.parse(res.responseText)
      val fileList = if (files.isEmpty) {
        Seq(result("files").keys.head)
      } else {
        files.filter(f => result("files").keys.exists(_.equalsIgnoreCase(f)))
      }
      fileList.map { fileName =>
        SourceFile(fileName, result("files")(fileName)("content").asString)
      }
    }.recover { case e => Seq(SourceFile("ScalaFiddle.scala", defaultCode)) }
  }

  def scheduleResets() = {
    dom.window.setInterval(() => Checker.reset(1000), 100)
  }
}
