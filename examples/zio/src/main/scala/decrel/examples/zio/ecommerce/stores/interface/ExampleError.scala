package decrel.examples.zio.ecommerce.stores.interface

sealed abstract class ExampleError(message: String) extends RuntimeException(message)

object ExampleError {
  final case class NotFound(entity: String, id: String)
      extends ExampleError(s"$entity not found: $id")
}
