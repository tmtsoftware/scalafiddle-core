package scalafiddle.router

import java.nio.file.Paths

import akka.actor.ActorSystem
import kamon.Kamon

import scalafiddle.router.cache.FileCache

object Server extends App {
  val system = ActorSystem()

  Kamon.start()

  val cache           = new FileCache(Paths.get(Config.cacheDir))
  val compilerManager = system.actorOf(CompilerManager.props, "CompilerManager")

  val webService = new WebService(system, cache, compilerManager)
}
