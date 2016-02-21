package fiddle

import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.util.Try

/**
 * Things that should eventually be pushed upstream to one of the libraries
 * that scala-js-fiddle depends on.
 */
object Util {

  def defer[T](t: =>T): Future[T] = {
    val p = Promise[T]()
    scala.scalajs.concurrent.JSExecutionContext.queue.execute(
      new Runnable{
        def run(): Unit = p.complete(Try(t))
      }
    )
    p.future
  }

  /**
   * Creates a HTML node from the given string
   */
  def createDom(s: String) = {
    val parser = new dom.DOMParser
    dom.document.adoptNode(
      parser.parseFromString(s, "text/html").documentElement
    ).lastChild.lastChild // double lastchild to go from html -> body -> thing
  }
  /**
   * Gets the element from the given ID and casts it,
   * shortening that common pattern
   */
  def getElem[T](id: String) = dom.document.getElementById(id).asInstanceOf[T]
}
