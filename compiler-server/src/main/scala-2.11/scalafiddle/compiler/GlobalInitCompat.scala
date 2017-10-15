package scalafiddle.compiler

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.reflect.io
import scala.tools.nsc
import scala.tools.nsc.Settings
import scala.tools.nsc.backend.JavaPlatform
import scala.tools.nsc.interactive.InteractiveAnalyzer
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.typechecker.Analyzer
import scala.tools.nsc.util.ClassPath.JavaContext
import scala.tools.nsc.util._
import scala.util.Try

object GlobalInitCompat {

  /**
    * Mixed in to make a Scala compiler run entirely in-memory,
    * loading its classpath and running macros from pre-loaded
    * in-memory files
    */
  trait InMemoryGlobal { g: scala.tools.nsc.Global =>
    def ctx: JavaContext
    def dirs: Vector[DirectoryClassPath]

    override lazy val plugins = List[Plugin](
      new org.scalajs.core.compiler.ScalaJSPlugin(this),
      new org.scalamacros.paradise.Plugin(this)
    )

    override lazy val platform: ThisPlatform = new JavaPlatform {
      val global: g.type     = g
      override def classPath = new JavaClassPath(dirs, ctx)
    }
  }

  val log = LoggerFactory.getLogger(getClass)

  private def inMemClassloader(libs: Seq[io.AbstractFile]): ClassLoader = {
    new ClassLoader(this.getClass.getClassLoader) {
      private val classCache = mutable.Map.empty[String, Option[Class[_]]]

      override def findClass(name: String): Class[_] = {

        def findClassInLibs(): Option[AbstractFile] = {
          val parts = name.split('.')
          libs
            .map(dir => {
              Try {
                parts
                  .dropRight(1)
                  .foldLeft[AbstractFile](dir)((parent, next) => parent.lookupName(next, directory = true))
                  .lookupName(parts.last + ".class", directory = false)
              } getOrElse null
            })
            .find(_ != null)
        }

        val res = classCache.getOrElseUpdate(
          name,
          findClassInLibs().map { f =>
            val data = f.toByteArray
            this.defineClass(name, data, 0, data.length)
          }
        )
        res match {
          case None =>
            log.error("Not Found Class " + name)
            throw new ClassNotFoundException()
          case Some(cls) =>
            cls
        }
      }
    }
  }

  def initGlobal(settings: Settings, reporter: StoreReporter, libs: Seq[io.AbstractFile]): nsc.Global = {
    val jCtx = new JavaContext()
    new nsc.Global(settings, reporter) with InMemoryGlobal { g =>
      def ctx  = jCtx
      def dirs = libs.map(new DirectoryClassPath(_, jCtx)).toVector

      override lazy val analyzer = new {
        val global: g.type = g
      } with Analyzer {
        val cl = inMemClassloader(libs)

        override def findMacroClassLoader() = cl
      }
    }
  }

  def initInteractiveGlobal(settings: Settings, reporter: StoreReporter, libs: Seq[io.AbstractFile]): nsc.interactive.Global = {
    val jCtx = new JavaContext()
    new nsc.interactive.Global(settings, reporter) with InMemoryGlobal { g =>
      def ctx  = jCtx
      def dirs = libs.map(new DirectoryClassPath(_, jCtx)).toVector
      override lazy val analyzer = new {
        val global: g.type = g
      } with InteractiveAnalyzer {
        val cl = inMemClassloader(libs)

        override def findMacroClassLoader() = cl
      }
    }
  }
}
