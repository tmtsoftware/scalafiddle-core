package scalafiddle.compiler

import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.reflect.io
import scala.tools.nsc
import scala.tools.nsc.Settings
import scala.tools.nsc.classpath.{AggregateClassPath, _}
import scala.tools.nsc.interactive.InteractiveAnalyzer
import scala.tools.nsc.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.plugins.Plugin
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.typechecker.Analyzer
import scala.util.Try

object GlobalInitCompat {
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

  private final def lookupPath(base: AbstractFile)(pathParts: Seq[String], directory: Boolean): AbstractFile = {
    log.debug(s"Looking for: ${pathParts}")
    var file: AbstractFile = base
    for (dirPart <- pathParts.init) {
      file = file.lookupName(dirPart, directory = true)
      if (file == null)
        return null
    }

    file.lookupName(pathParts.last, directory = directory)
  }

  private def buildClassPath(file: AbstractFile) = new VirtualDirectoryClassPath(new VirtualDirectory(file.name, None)) {
    override def getSubDir(packageDirName: String): Option[AbstractFile] = {
      Option(lookupPath(file)(packageDirName.split('/'), directory = true))
    }

    override def findClassFile(className: String): Option[AbstractFile] = {
      val relativePath = FileUtils.dirPath(className) + ".class"
      Option(lookupPath(file)(relativePath.split('/'), directory = false))
    }
  }

  def initGlobal(settings: Settings, reporter: StoreReporter, libs: Seq[io.AbstractFile]): nsc.Global = {
    val cp = new AggregateClassPath(libs.map(buildClassPath))
    val cl = inMemClassloader(libs)

    new nsc.Global(settings, reporter) { g =>
      GlobalInitCompat.this.log.debug("Init global")
      override def classPath = cp

      override lazy val plugins = List[Plugin](
        new org.scalajs.core.compiler.ScalaJSPlugin(this),
        new org.scalamacros.paradise.Plugin(this)
      )

      override lazy val platform: ThisPlatform = new GlobalPlatform {
        GlobalInitCompat.this.log.debug("Init platform")
        override val global    = g
        override val settings  = g.settings
        override def classPath = cp
      }

      override lazy val analyzer = new {
        val global: g.type = g
      } with Analyzer {
        GlobalInitCompat.this.log.debug("Init analyzer")
        override def findMacroClassLoader() = cl
      }
    }
  }

  def initInteractiveGlobal(settings: Settings,
                            reporter: StoreReporter,
                            libs: Seq[io.AbstractFile]): nsc.interactive.Global = {
    val cp = new AggregateClassPath(libs.map(buildClassPath))
    new nsc.interactive.Global(settings, reporter) { g =>
      override def classPath = cp

      override lazy val plugins = List[Plugin](
        new org.scalajs.core.compiler.ScalaJSPlugin(this),
        new org.scalamacros.paradise.Plugin(this)
      )

      override lazy val platform: ThisPlatform = new GlobalPlatform {
        override val global    = g
        override val settings  = g.settings
        override def classPath = cp
      }

      override lazy val analyzer = new {
        val global: g.type = g
      } with InteractiveAnalyzer {
        val cl = inMemClassloader(libs)

        override def findMacroClassLoader() = cl
      }
    }
  }
}
