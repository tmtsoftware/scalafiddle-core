package scalafiddle.compiler.cache

import scala.tools.nsc

object AutoCompleteCache extends LRUCache[nsc.interactive.Global]("AutoComplete") {}
