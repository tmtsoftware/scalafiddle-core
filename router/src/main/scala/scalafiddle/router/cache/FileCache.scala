package scalafiddle.router.cache
import java.io.{File, FileFilter}
import java.nio.file.{Files, Path, StandardOpenOption}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class FileCache(cacheDir: Path) extends Cache {
  cacheDir.toFile.mkdirs()

  override def get(id: String, expiration: Int): Future[Option[Array[Byte]]] = {
    val file = cacheDir.resolve(s"$id-$expiration").toFile
    Future {
      if (file.exists()) {
        if ((System.currentTimeMillis() - file.lastModified()) / 1000 < expiration) {
          Try(Files.readAllBytes(file.toPath)).toOption
        } else {
          // remove expired file
          Try(file.delete())
          None
        }
      } else {
        None
      }
    }
  }

  override def put(id: String, data: Array[Byte], expiration: Int): Future[Unit] = {
    val file = cacheDir.resolve(s"$id-$expiration")
    Future {
      Files.write(file, data, StandardOpenOption.CREATE)
    }
  }

  override def clean(expiration: Int): Unit = {
    val expStr = expiration.toString
    cacheDir.toFile
      .listFiles(new FileFilter {
        override def accept(pathname: File): Boolean = pathname.getName.endsWith(expStr)
      })
      .foreach { file =>
        file.delete()
      }
  }

}
