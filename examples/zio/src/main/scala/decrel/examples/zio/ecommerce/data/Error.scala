package decrel.examples.zio.ecommerce.data

sealed trait Error extends RuntimeException

object Error {
  final case class NotFound(entity: String, id: String) extends Error {
    override def getMessage: String =
      s"$entity not found: $id"
  }
}
