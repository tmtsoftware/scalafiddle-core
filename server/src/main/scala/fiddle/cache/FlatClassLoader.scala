package fiddle.cache

class FlatClassLoader(ffs: FlatFileSystem) {

  object loader extends ClassLoader(this.getClass.getClassLoader) {
    def defClz(name: String, data: Array[Byte], length: Int): Class[_] = defineClass(name, data, 0, length)
  }

  def findClass(name: String) = {
    try {
      System.err.println(s"Caching $name")
      val data = ffs.load(name + ".class")
      loader.defClz(name, data, data.length)
    } catch {
      case e: Exception =>
        throw new ClassNotFoundException(name)
    }
  }
}
