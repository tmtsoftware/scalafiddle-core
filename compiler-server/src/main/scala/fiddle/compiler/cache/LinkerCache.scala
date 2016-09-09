package fiddle.compiler.cache

import org.scalajs.core.tools.linker.Linker

object LinkerCache extends LRUCache[Linker]("Linker") {

}
