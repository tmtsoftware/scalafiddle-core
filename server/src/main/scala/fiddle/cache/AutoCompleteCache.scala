package fiddle.cache

import scala.tools.nsc

object AutoCompleteCache extends LRUCache[nsc.interactive.Global] {
}
