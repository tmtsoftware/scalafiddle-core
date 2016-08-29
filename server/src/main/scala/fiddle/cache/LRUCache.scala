package fiddle.cache

import fiddle.ExtLib

import scala.collection.mutable

class LRUCache[T] {
  protected val cacheSize = 1
  private val cache = mutable.ListMap.empty[Int, T]

  def getOrUpdate(libs: Set[ExtLib], update: => T): T = {
    val hash = hashLibs(libs)
    cache.get(hash) match {
      case Some(value) =>
        cache += hash -> value
        value
      case None =>
        val value = update
        if(cache.size >= cacheSize) {
          cache.iterator.drop(cacheSize - 1).foreach(cache -= _._1)
        }
        cache += hash -> value
        value
    }
  }

  def hashLibs(libs: Set[ExtLib]): Int = {
    libs.foldLeft(0)(_ ^ _.hashCode())
  }

  def remove(libs: Set[ExtLib]): Unit = {
    val hash = hashLibs(libs)
    cache.remove(hash)
  }
}


