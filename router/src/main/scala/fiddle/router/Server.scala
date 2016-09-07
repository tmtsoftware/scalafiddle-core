package fiddle.router

import java.nio.file.Paths

import akka.actor.ActorSystem
import fiddle.router.cache.FileCache

object Server extends App {
  val system = ActorSystem()

  val router = system.actorOf(Router.props(Config.compiler.host, Config.compiler.port))
  val cache = new FileCache(Paths.get(Config.cacheDir))

  val webService = new WebService(system, router, cache)
}
