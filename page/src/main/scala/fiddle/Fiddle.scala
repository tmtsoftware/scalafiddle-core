package fiddle

import org.scalajs.dom
import org.scalajs.dom.html

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.Try
import scalatags.JsDom.all._

/**
  * API for things that belong to the page, and are useful to both the fiddle
  * client, user code as well as exported read-only pages.
  */
@JSExportTopLevel("Fiddle")
object Fiddle {

  object colors {
    def red    = span(color := "#E95065")
    def blue   = span(color := "#46BDDF")
    def green  = span(color := "#52D273")
    def yellow = span(color := "#E5C453")
    def orange = span(color := "#E57255")
  }

  /**
    * Gets the element from the given ID and casts it,
    * shortening that common pattern
    */
  def getElem[T](id: String) = dom.document.getElementById(id).asInstanceOf[T]

  val sandbox = getElem[html.Div]("container")
  val canvas  = getElem[html.Canvas]("canvas")
  val draw    = canvas.getContext("2d").asInstanceOf[dom.CanvasRenderingContext2D]
  val panel   = getElem[html.Div]("output")

  def println(ss: Any) = {
    ss match {
      case null =>
        print(div("null"))
      case m: Modifier @unchecked =>
        print(div(m))
      case _ =>
        print(div(ss.toString))
    }
  }

  def printDiv(ss: Modifier*) = {
    print(div(ss))
  }

  def print(ss: Modifier*) = {
    ss.foreach(_.applyTo(panel))
    panel.scrollTop = panel.scrollHeight - panel.clientHeight
  }

  def clear() = {
    // clear panel and canvas
    panel.innerHTML = ""
    canvas.height = sandbox.clientHeight
    canvas.width = sandbox.clientWidth
    draw.clearRect(0, 0, 10000, 10000)
  }

  def defer[T](t: => T): Future[T] = {
    val p = Promise[T]()
    scala.scalajs.concurrent.JSExecutionContext.queue.execute(
      new Runnable {
        def run(): Unit = p.complete(Try(t))
      }
    )
    p.future
  }

  def scheduleOnce(delay: Int)(f: => Unit) = {
    val handle = js.timers.setTimeout(delay)(f)
    handle
  }

  def schedule(interval: Int)(f: => Unit) = {
    val handle = js.timers.setInterval(interval)(f)
    handle
  }
}
