package scalafiddle.client

import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSGlobalScope

class JsVal(val value: js.Dynamic) {
  def get(name: String): Option[JsVal] = {
    (value.selectDynamic(name): Any) match {
      case () => None
      case v  => Some(JsVal(v.asInstanceOf[js.Dynamic]))
    }
  }

  def apply(name: String): JsVal = get(name).get
  def apply(index: Int): JsVal   = value.asInstanceOf[js.Array[JsVal]](index)

  def keys: Seq[String]  = js.Object.keys(value.asInstanceOf[js.Object]).toSeq.map(x => x: String)
  def values: Seq[JsVal] = keys.map(x => JsVal(value.selectDynamic(x)))

  def isDefined: Boolean = !js.isUndefined(value)
  def isNull: Boolean    = value eq null

  def asDouble: Double   = value.asInstanceOf[Double]
  def asBoolean: Boolean = value.asInstanceOf[Boolean]
  def asString: String   = value.asInstanceOf[String]

  override def toString(): String = js.JSON.stringify(value)
}

object JsVal {
  implicit def jsVal2jsAny(v: JsVal): js.Any = v.value

  implicit def jsVal2String(v: JsVal): String = v.toString

  def parse(value: String) = new JsVal(js.JSON.parse(value))

  def apply(value: js.Any) = new JsVal(value.asInstanceOf[js.Dynamic])

  def obj(keyValues: (String, js.Any)*) = {
    val obj = new js.Object().asInstanceOf[js.Dynamic]
    for ((k, v) <- keyValues) {
      obj.updateDynamic(k)(v.asInstanceOf[js.Any])
    }
    new JsVal(obj)
  }

  def arr(values: js.Any*) = {
    new JsVal(values.toJSArray.asInstanceOf[js.Dynamic])
  }
}

class Logger(val f: String => Unit)

/**
  * Used to mark a Future as a task which returns Unit, making
  * sure to print the error and stack trace if it fails.
  */
object task {
  def *[T](f: Future[T])(implicit ec: ExecutionContext, logger: Logger) = {
    f.map(_ => ()).recover {
      case e =>
        logger.f(e.toString)
        e.printStackTrace()
    }
  }
}

object EventTracker {
  def isScriptLoaded = js.Dynamic.global.ga.isInstanceOf[js.Function]
  def sendEvent(category: String, action: String, label: String): Unit = {
    if (isScriptLoaded) GoogleAnalytics.ga("send", "event", category, action, label)
  }
  def sendEvent(category: String, action: String, label: String, value: Long): Unit = {
    if (isScriptLoaded) GoogleAnalytics.ga("send", "event", category, action, label, value)
  }
}

@js.native
@JSGlobalScope
object GoogleAnalytics extends js.Object {
  def ga(send: String, event: String, category: String, action: String, label: String): Unit              = js.native
  def ga(send: String, event: String, category: String, action: String, label: String, value: Long): Unit = js.native
}
