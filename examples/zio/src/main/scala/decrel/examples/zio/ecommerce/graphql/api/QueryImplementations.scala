package decrel.examples.zio.ecommerce.graphql.api

import decrel.examples.zio.ecommerce.data
import decrel.examples.zio.ecommerce.data.Error
import decrel.examples.zio.ecommerce.graphql.protocol
import decrel.examples.zio.ecommerce.graphql.transformers.Transformers
import decrel.examples.zio.ecommerce.stores.interface.Proofs
import decrel.syntax._
import zio.{ IO, URLayer, ZIO, ZLayer }
import zio.query.ZQuery

trait QueryImplementations {
  def customer(id: String): protocol.TaskQuery[Option[protocol.Customer]]
  def order(id: String): protocol.TaskQuery[Option[protocol.Order]]
}

object QueryImplementations {
  val live: URLayer[Proofs & Transformers, QueryImplementations] =
    ZLayer.derive[Impl]

  final class Impl(proofs: Proofs, transformers: Transformers) extends QueryImplementations {
    import proofs.given

    private def fetchCustomer(id: data.Customer.Id): IO[Error, Option[data.Customer]] =
      data.Customer.fetch.toZIO(id).map(Some(_)).catchSome {
        case _: Error.NotFound => ZIO.none
      }

    private def fetchOrder(id: data.Order.Id): IO[Error, Option[data.Order]] =
      data.Order.fetch.toZIO(id).map(Some(_)).catchSome {
        case _: Error.NotFound => ZIO.none
      }

    override def customer(id: String): protocol.TaskQuery[Option[protocol.Customer]] =
      ZQuery.fromZIO(fetchCustomer(data.Customer.Id(id))).map(_.map(transformers.customer))

    override def order(id: String): protocol.TaskQuery[Option[protocol.Order]] =
      ZQuery.fromZIO(fetchOrder(data.Order.Id(id))).map(_.map(transformers.order))
  }
}
