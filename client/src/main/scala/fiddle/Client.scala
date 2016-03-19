package fiddle

import java.net.URLDecoder

import fiddle.JsVal.jsVal2jsAny
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw._
import upickle.default._

import scala.async.Async.{async, await}
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.timers.{SetIntervalHandle, SetTimeoutHandle}
import scala.scalajs.niocharset.StandardCharsets

case class SourceFile(name: String, code: String, prefix: List[String] = Nil, postfix: List[String] = Nil,
  template: Option[String] = None)

class Client(templateId: String, envId: String) {
  implicit val RedLogger = fiddle.Client.RedLogger
  var sourceFiles = Seq(SourceFile("ScalaFiddle.scala", ""))
  var currentSource = sourceFiles.head.name

  def currentSourceFile = sourceFiles.find(_.name == currentSource).get

  def currentTemplate = currentSourceFile.template.getOrElse(templateId)

  val command = Channel[Future[CompilerResponse]]()

  def exec(s: String) = {
    Client.clear()

    try {
      js.eval(s)
      js.eval("ScalaFiddle().main()")
    } catch {
      case e: Throwable =>
        Client.logError(e.toString)
        showError(e.toString)
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

  def showError(errStr: String): Unit = {
    import scalatags.JsDom.all._
    showStatus("Errors")
    Fiddle.clear()
    Fiddle.println(pre(cls := "error", errStr))
  }

  def reconstructSource(source: String, srcFile: SourceFile): String = {
    // use map instead of mkString to prevent an empty list from generating a single line
    srcFile.prefix.map(_ + "\n").mkString + source + srcFile.postfix.map(_ + "\n").mkString
  }

  def encodeSource(source: String): String = {
    import com.github.marklister.base64.Base64._
    implicit def scheme: B64Scheme = base64Url
    Encoder(reconstructSource(source, currentSourceFile).getBytes(StandardCharsets.UTF_8)).toBase64
  }

  def compileServer(code: String, opt: String): Future[CompilerResponse] = {
    val tag = s"${if(Client.initializing) "initial-" else ""}$opt"
    val startTime = System.nanoTime()
    Ajax.get(
      url = s"/compile?env=$envId&template=$currentTemplate&opt=$opt&source=${encodeSource(code)}"
    ).map { res =>
      val compileTime = (System.nanoTime() - startTime)/1000000
      EventTracker.sendEvent("compile", tag, currentSource, compileTime)
      read[CompilerResponse](res.responseText)
    } recover {
      case e: dom.ext.AjaxException =>
        showError(s"Error: ${e.xhr.responseText}")
        throw e
      case e: Throwable =>
        showError(e.toString)
        throw e
    }
  }

  def fullOpt = {
    beginCompilation()
    command.update(compileServer(editor.code, "full"))
  }

  def fastOpt = {
    beginCompilation()
    command.update(compileServer(editor.code, "fast"))
  }

  val runIcon = dom.document.getElementById("run-icon").asInstanceOf[HTMLElement]
  val resetIcon = dom.document.getElementById("reset-icon").asInstanceOf[HTMLElement]
  val shareIcon = dom.document.getElementById("share-icon").asInstanceOf[HTMLElement]
  val shareBox = dom.document.getElementById("sharebox").asInstanceOf[HTMLElement]
  val shareLink = dom.document.getElementById("sharelink").asInstanceOf[HTMLInputElement]
  val gistButton = dom.document.getElementById("gist-button").asInstanceOf[HTMLButtonElement]
  val outputTag = dom.document.getElementById("output-tag").asInstanceOf[HTMLElement]
  val editorContainerDiv = dom.document.getElementById("editorContainer").asInstanceOf[HTMLElement]
  val fiddleSelectorDiv = dom.document.getElementById("fiddleSelectorDiv").asInstanceOf[HTMLElement]
  val fiddleSelector = dom.document.getElementById("fiddleSelector").asInstanceOf[HTMLSelectElement]

  // attach handlers to icons
  runIcon.onclick = (e: MouseEvent) => {
    if (e.shiftKey)
      fullOpt
    else
      fastOpt
  }

  resetIcon.onclick = (e: MouseEvent) => {
    EventTracker.sendEvent("reset", "reset", currentSource)
    selectSource(currentSource)
    editor.focus()
  }

  shareIcon.onclick = (e: MouseEvent) => {
    share()
  }

  shareBox.onmousedown = (e: MouseEvent) => {
    e.stopPropagation()
  }

  gistButton.onclick = (e: MouseEvent) => {
    save()
    closeShare()
  }

  fiddleSelector.onchange = (e: Event) => {
    val sel = fiddleSelector.options(fiddleSelector.selectedIndex).text
    dom.console.log(s"Fiddle selected $sel")
    updateSource(editor.code)
    selectSource(sel)
  }

  val outsideClickHandler: MouseEvent => Unit = e => closeShare()

  def share(): Unit = {
    EventTracker.sendEvent("share", "open", currentSource)
    shareBox.style.display = "inherit"
    dom.document.body.addEventListener("mousedown", outsideClickHandler)
    shareLink.value = dom.window.location.href
  }

  def closeShare(): Unit = {
    shareBox.style.display = "none"
    dom.document.body.removeEventListener("mousedown", outsideClickHandler)
  }
  def showStatus(status: String) =
    outputTag.innerHTML = status

  val templateOverride = """\s*// \$Template (\w.+)""".r
  val fiddleStart = """\s*// \$FiddleStart\s*$""".r
  val fiddleEnd = """\s*// \$FiddleEnd\s*$""".r

  // separate source code into pre,main,post blocks
  def extractCode(src: SourceFile): SourceFile = {
    val lines = src.code.split('\n')
    val (template, pre, main, post) = lines.foldLeft((Option.empty[String], List.empty[String], List.empty[String], List.empty[String])) {
      case ((customTemplate, preList, mainList, postList), line) => line match {
        case templateOverride(name) =>
          (Some(name), line :: preList, mainList, postList)
        case fiddleStart() =>
          (customTemplate, line :: mainList ::: preList, Nil, Nil)
        case fiddleEnd() =>
          (customTemplate, preList, mainList, line :: postList)
        case l if postList.nonEmpty =>
          (customTemplate, preList, mainList, line :: postList)
        case _ =>
          (customTemplate, preList, line :: mainList, postList)
      }
    }
    SourceFile(src.name, main.reverse.mkString("", "\n", "\n"), pre.reverse, post.reverse, template)
  }

  def setSources(sources: Seq[SourceFile]): Unit = {
    import scalatags.JsDom.all._

    sourceFiles = sources.map(extractCode)
    fiddleSelector.innerHTML = ""
    sourceFiles.foreach { source =>
      fiddleSelector.add(option(value := source.name)(source.name).render)
    }
    if (sourceFiles.size > 1) {
      fiddleSelectorDiv.style.display = "inherit"
      editorContainerDiv.classList.add("selectorVisible")
    } else {
      fiddleSelectorDiv.style.display = "none"
      editorContainerDiv.classList.remove("selectorVisible")
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
      case sf: SourceFile if sf.name == currentSource => sf.copy(code = code)
      case sf => sf
    }
  }

  def compile(res: Future[CompilerResponse]): Future[Option[String]] = {

    res.map { response =>
      endCompilation()
      val prefixLines = currentSourceFile.prefix.size
      editor.setAnnotations(response.annotations.map(a => a.copy(row = a.row - prefixLines)))
      if (response.jsCode.isEmpty) {
        // show compiler errors in output
        val allErrors = response.annotations.map { ann =>
          s"ScalaFiddle.scala:${ann.row + 1 - prefixLines}: ${ann.tpe}: ${ann.text.mkString("\n")}"
        }.mkString("\n")
        showError(allErrors)
      }
      response.jsCode
    }.recover { case e: Exception =>
      endCompilation()
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
    val startTime = System.nanoTime()

    val f = Ajax.get(
      url = s"/complete?env=$envId&template=$currentTemplate&flag=$flag&offset=$intOffset&source=${encodeSource(code)}"
    ).map { res =>
      val completeTime = (System.nanoTime() - startTime)/1000000
      EventTracker.sendEvent("complete", "complete", currentSource, completeTime)
      read[List[(String, String)]](res.responseText)
    } recover {
      case e: dom.ext.AjaxException =>
        showError(s"Error: ${e.xhr.responseText}")
        throw e
      case e: Throwable =>
        showError(e.toString)
        throw e
    }

    val res = await(f)
    res
  }

  def save(): Unit = task * async {
    val files = new js.Object().asInstanceOf[js.Dynamic]
    updateSource(editor.code)
    sourceFiles.foreach { src =>
      files.updateDynamic(src.name)(JsVal.obj("content" -> reconstructSource(src.code, src)))
    }
    val data = JsVal.obj(
      "description" -> "ScalaFiddle gist",
      "public" -> true,
      "files" -> files
    ).toString()

    val res = await(Ajax.post("https://api.github.com/gists", data = data))
    val result = JsVal.parse(res.responseText)
    import scalatags.JsDom.all._
    val gistId = result("id").asString
    val url = s"https://gist.github.com/anonymous/$gistId"
    showStatus("Uploaded")
    EventTracker.sendEvent("share", "save", currentSource)
    Fiddle.clear()
    Fiddle.println("ScalaFiddle uploaded to a gist at ", a(href := url, target := "_blank")(url))
    // build a link to show the uploaded source in Scala Fiddle
    val params = Client.queryParams
      .filterKeys(name => name != "gist" && name != "source")
      .updated("gist", gistId)
      .updated("files", sourceFiles.map(_.name).mkString(","))
      .map { case (k, v) => s"$k=${js.URIUtils.encodeURIComponent(v)}" }
      .mkString("&")
    val sfUrl = s"/embed?$params"
    Fiddle.println("Open the uploaded fiddle ", a(href := sfUrl, target := "_blank")("in a new tab"))
    dom.console.log(s"ScalaFiddle uploaded to $url")
  }
}

@JSExport("Client")
object Client {
  implicit val RedLogger = new Logger(logError)

  var intervalHandles = List.empty[SetIntervalHandle]
  var timeoutHandles = List.empty[SetTimeoutHandle]


  dom.window.onerror = { (event: dom.Event, source: String, fileno: Int, columnNumber: Int) =>
    dom.console.log("dom.onerror")
    Client.logError(event.toString)
  }

  def parseUriParameters(search: String): Map[String, String] = {
    search.drop(1).split('&').filter(_.nonEmpty).map { part =>
      val pair = part.split("=")
      val key = URLDecoder.decode(pair(0), "UTF-8")
      val value = if (pair.length > 1) URLDecoder.decode(pair(1), "UTF-8") else ""
      key -> value
    }.toMap
  }

  var initializing = true
  val queryParams = parseUriParameters(dom.window.location.search)
  val templateId = queryParams.getOrElse("template", "default")
  val envId = queryParams.getOrElse("env", "default")

  dom.console.log(s"queryParams: $queryParams, templateId: $templateId")

  def logError(s: String): Unit = {
    dom.console.error(s)
  }

  def clear() = {
    dom.console.log(s"Clearing ${timeoutHandles.size} timeouts and ${intervalHandles.size} intervals")
    // clear all timers
    timeoutHandles.foreach(js.timers.clearTimeout)
    timeoutHandles = Nil
    intervalHandles.foreach(js.timers.clearInterval)
    intervalHandles = Nil

    Fiddle.clear()
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
  def main(useFast: Boolean = false): Unit = task * async {
    clear()
    Editor.initEditor
    val client = new Client(templateId, envId)
    // is a gist specified?
    if (queryParams.contains("gist")) {
      val gistId = queryParams("gist")
      val files = queryParams.get("files").map(_.split(',').toList.filter(_.nonEmpty)).getOrElse(Nil)
      client.showStatus("Loading")
      val sources = await(load(gistId, files))
      client.setSources(sources)
      if (useFast)
        client.fastOpt
      else
        client.fullOpt
    } else if (queryParams.contains("source")) {
      val srcCode = queryParams("source")
      client.showStatus("Loading")
      // check for sub-fiddles
      val sources = await(Future(parseFiddles(srcCode)))
      client.setSources(sources)
      if (useFast)
        client.fastOpt
      else
        client.fullOpt
    }
    initializing = false
  }

  val defaultCode =
    """
      |println("Looks like there was an error loading the default Gist!")
      |println("Loading an empty application so you can get started")
    """.stripMargin

  def load(gistId: String, files: Seq[String]): Future[Seq[SourceFile]] = {
    val gistUrl = "https://gist.github.com/" + gistId
    Ajax.get("https://api.github.com/gists/" + gistId).map { res =>
      val result = JsVal.parse(res.responseText)
      val fileList = if (files.isEmpty) {
        Seq(result("files").keys.head)
      } else if (files == Seq("*")) {
        // take all files from the gist
        result("files").keys
      } else {
        files.filter(f => result("files").keys.exists(_.equalsIgnoreCase(f)))
      }
      fileList.map { fileName =>
        SourceFile(fileName, result("files")(fileName)("content").asString)
      }
    }.recover { case e => Seq(SourceFile("ScalaFiddle.scala", defaultCode)) }
  }

  @JSExport
  def addTimeoutHandle(handle: SetTimeoutHandle): Unit = {
    timeoutHandles ::= handle
  }

  @JSExport
  def addIntervalHandle(handle: SetIntervalHandle): Unit = {
    intervalHandles ::= handle
  }
}
