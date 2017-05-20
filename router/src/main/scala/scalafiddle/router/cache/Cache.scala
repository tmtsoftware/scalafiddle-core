package scalafiddle.router.cache

import scala.concurrent.Future

trait Cache {
  def get(id: String, expiration: Int): Future[Option[Array[Byte]]]

  def put(id: String, data: Array[Byte], expiration: Int): Future[Unit]
}
