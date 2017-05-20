package scalafiddle.compiler.cache

import scala.tools.nsc

object CompilerCache extends LRUCache[nsc.Global]("Compiler") {}
