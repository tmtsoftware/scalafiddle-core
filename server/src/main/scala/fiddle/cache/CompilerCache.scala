package fiddle.cache

import scala.tools.nsc

object CompilerCache extends LRUCache[nsc.Global] {
}
