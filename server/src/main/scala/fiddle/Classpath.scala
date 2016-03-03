package fiddle

import java.io._
import java.nio.file.Files
import java.util.zip.ZipInputStream

import akka.actor.ActorSystem
import org.scalajs.core.tools.io._
import spray.client.pipelining._
import spray.http._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.reflect.io.{Streamable, VirtualDirectory}

/**
  * Loads the jars that make up the classpath of the scala-js-fiddle
  * compiler and re-shapes it into the correct structure to satisfy
  * scala-compile and scalajs-tools
  */
object Classpath {
  implicit val system = ActorSystem()
  import system.dispatcher

  val timeout = 60.seconds
  val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  val baseLibs = Seq(
    s"/scala-library-${Config.scalaVersion}.jar",
    s"/scala-reflect-${Config.scalaVersion}.jar",
    s"/scalajs-library_${Config.scalaMainVersion}-${Config.scalaJSVersion}.jar",
    s"/page_sjs${Config.scalaJSMainVersion}_${Config.scalaMainVersion}-${Config.version}.jar"
  )

  val repoSJSRE = """([^ %]+) *%%% *([^ %]+) *% *([^ %]+)""".r
  val repoRE = """([^ %]+) *%% *([^ %]+) *% *([^ %]+)""".r
  val repoBase = "https://repo1.maven.org/maven2"
  val sjsVersion = s"_sjs${Config.scalaJSMainVersion}_${Config.scalaMainVersion}"

  def buildRepoUri(ref: String) = {
    ref match {
      case repoSJSRE(group, artifact, version) =>
        s"$repoBase/${group.replace('.', '/')}/$artifact$sjsVersion/$version/$artifact$sjsVersion-$version.jar"
      case repoRE(group, artifact, version) =>
        s"$repoBase/${group.replace('.', '/')}/${artifact}_${Config.scalaMainVersion}/$version/${artifact}_${Config.scalaMainVersion}-$version.jar"
      case _ => ref
    }
  }

  def loadExtLib(ref: String) = {
    val uri = buildRepoUri(ref)
    val name = uri.split('/').last
    // check if it has been loaded already
    val f = new File(Config.libCache, name)
    if (f.exists()) {
      println(s"Loading $name from ${Config.libCache}")
      Future {(name, Files.readAllBytes(f.toPath))}
    } else {
      println(s"Loading $name from $uri")
      f.getParentFile.mkdirs()
      pipeline(Get(uri)).map { response =>
        val data = response.entity.data.toByteArray
        println(s"Storing $name with ${data.length} bytes to cache")
        // save to cache
        Files.write(f.toPath, data)
        (name, data)
      } recover {
        case e: Exception =>
          println(s"Error loading $uri: $e")
          throw e
      }
    }
  }

  /**
    * In memory cache of all the jars used in the compiler. This takes up some
    * memory but is better than reaching all over the filesystem every time we
    * want to do something.
    */
  lazy val loadedFiles = {
    println("Loading files...")
    val extFilesFut = Future.sequence(Config.extLibs.map(loadExtLib))
    // load all external libs in parallel using spray-client
    val jarFiles = baseLibs.par.map { name =>
      val stream = getClass.getResourceAsStream(name)
      println(s"Loading resource $name")
      if (stream == null) {
        throw new Exception(s"Classpath loading failed, jar $name not found")
      }
      name -> Streamable.bytes(stream)
    }.seq

    val bootFiles = for {
      prop <- Seq(/*"java.class.path", */ "sun.boot.class.path")
      path <- System.getProperty(prop).split(System.getProperty("path.separator"))
      vfile = scala.reflect.io.File(path)
      if vfile.exists && !vfile.isDirectory
    } yield {
      path.split("/").last -> vfile.toByteArray()
    }
    val extFiles = Await.result(extFilesFut, timeout)
    println("Files loaded...")
    jarFiles ++ bootFiles ++ extFiles
  }

  /**
    * The loaded files shaped for Scalac to use
    */
  lazy val scalac = (for ((name, bytes) <- loadedFiles.par) yield {
    println(s"Loading $name for Scalac")
    val in = new ZipInputStream(new ByteArrayInputStream(bytes))
    val entries = Iterator
      .continually(in.getNextEntry)
      .takeWhile(_ != null)
      .map((_, Streamable.bytes(in)))

    val dir = new VirtualDirectory(name, None)
    for {
      (e, data) <- entries
      if !e.isDirectory
    } {
      val tokens = e.getName.split("/")
      var d = dir
      for (t <- tokens.dropRight(1)) {
        d = d.subdirectoryNamed(t).asInstanceOf[VirtualDirectory]
      }
      val f = d.fileNamed(tokens.last)
      val o = f.bufferedOutput
      o.write(data)
      o.close()
    }
    println(dir.size)
    dir
  }).seq
  /**
    * The loaded files shaped for Scala-Js-Tools to use
    */
  lazy val scalajs = {
    println("Loading scalaJSClassPath")
    val loadedJars: Seq[IRFileCache.IRContainer] = {
      for ((name, bytes) <- loadedFiles) yield {
        val jarFile = (new MemVirtualBinaryFile(name) with VirtualJarFile)
          .withContent(bytes)
          .withVersion(Some(name)) // unique through the lifetime of the server
        IRFileCache.IRContainer.Jar(jarFile)
      }
    }
    val cache = (new IRFileCache).newCache
    val res = cache.cached(loadedJars)
    println("Loaded scalaJSClassPath")
    res
  }
}
