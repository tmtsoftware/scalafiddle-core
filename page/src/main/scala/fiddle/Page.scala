package fiddle

import scala.scalajs.js
import js.annotation.JSExport
import org.scalajs.dom
import dom.html
import scalatags.JsDom.all._

/**
 * API for things that belong to the page, and are useful to both the fiddle
 * client, user code as well as exported read-only pages.
 */
@JSExport
object Page{
  def red = span(color:="#E95065")
  def blue = span(color:="#46BDDF")
  def green = span(color:="#52D273")
  def yellow = span(color:="#E5C453")
  def orange = span(color:="#E57255")

  def sandbox = Util.getElem[html.Div]("sandbox")
  def canvas = Util.getElem[html.Canvas]("canvas")
  def renderer = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  def output = Util.getElem[html.Div]("output")
  def logspam = Util.getElem[html.Pre]("logspam")
  def source = Util.getElem[html.Div]("source")

  def println(ss: Modifier*) = {
    print(div(ss: _*))
  }

  def print(ss: Modifier*) = {
    ss.foreach(_.applyTo(output))
    output.scrollTop = output.scrollHeight - output.clientHeight
  }

  def clear() = {
    output.innerHTML = ""
    canvas.height = sandbox.clientHeight
    canvas.width = sandbox.clientWidth
    val tmp = renderer.fillStyle
    renderer.fillStyle = "rgb(20, 20, 20)"
    renderer.clearRect(0, 0, 10000, 10000)
    renderer.fillStyle = tmp
  }

  def scroll(px: Int) = {
    dom.console.log("Scrolling", px)
    output.scrollTop = output.scrollTop + px
  }

  def logln(s: Modifier*): Unit = {
    log(div(s:_*))
  }

  def log(s: Modifier*): Unit = {
    s.foreach(_.applyTo(logspam))
    logspam.scrollTop = 1000000000
  }

  val compiled = Util.getElem[html.Div]("compiled").textContent
}
