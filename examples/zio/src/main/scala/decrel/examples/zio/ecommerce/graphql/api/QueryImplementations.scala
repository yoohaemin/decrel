package decrel.examples.zio.ecommerce.graphql.api

import decrel.examples.zio.ecommerce.data
import decrel.examples.zio.ecommerce.graphql.protocol
import decrel.examples.zio.ecommerce.graphql.transformers.Transformers
import decrel.examples.zio.ecommerce.stores.interface.ExampleError
import decrel.examples.zio.ecommerce.stores.interface.Proofs
import decrel.syntax._
import zio.{ IO, URLayer, ZIO, ZLayer }
import zio.query.ZQuery

trait QueryImplementations {
  def customer(id: String): protocol.TaskQuery[Option[protocol.Customer]]
  def order(id: String): protocol.TaskQuery[Option[protocol.Order]]
  def checkoutView(id: String): protocol.TaskQuery[Option[protocol.CheckoutView]]
  def adminOrderView(id: String): protocol.TaskQuery[Option[protocol.AdminOrderView]]
}

object QueryImplementations {
  val live: URLayer[Proofs & Transformers, QueryImplementations] =
    ZLayer.derive[Impl]

  final class Impl(proofs: Proofs, transformers: Transformers) extends QueryImplementations {
    import proofs.given

    private def fetchCustomer(id: data.Customer.Id): IO[ExampleError, Option[data.Customer]] =
      data.Customer.fetch.toZIO(id).map(Some(_)).catchSome {
        case _: ExampleError.NotFound => ZIO.none
      }

    private def fetchOrder(id: data.Order.Id): IO[ExampleError, Option[data.Order]] =
      data.Order.fetch.toZIO(id).map(Some(_)).catchSome {
        case _: ExampleError.NotFound => ZIO.none
      }

    override def customer(id: String): protocol.TaskQuery[Option[protocol.Customer]] =
      ZQuery.fromZIO(fetchCustomer(data.Customer.Id(id))).map(_.map(transformers.customer))

    override def order(id: String): protocol.TaskQuery[Option[protocol.Order]] =
      ZQuery.fromZIO(fetchOrder(data.Order.Id(id))).map(_.map(transformers.order))

    override def checkoutView(id: String): protocol.TaskQuery[Option[protocol.CheckoutView]] =
      ZQuery.fromZIO(fetchOrder(data.Order.Id(id))).flatMap {
        case Some(orderValue) =>
          ZQuery.fromZIO(
            ((data.Order.customer <>: data.Customer.loyaltyTier) &
              (data.Order.items <>: (data.Item.product & data.Item.price)) &
              data.Order.shipment).toZIO(orderValue)
          ).map {
            case (customerValue, loyaltyTierValue, linesValue, shipmentValue) =>
              Some(
                transformers.checkoutView(
                  orderValue,
                  customerValue,
                  loyaltyTierValue,
                  linesValue,
                  shipmentValue
                )
              )
          }
        case None =>
          ZQuery.succeed(None)
      }

    override def adminOrderView(id: String): protocol.TaskQuery[Option[protocol.AdminOrderView]] =
      ZQuery.fromZIO(fetchOrder(data.Order.Id(id))).flatMap {
        case Some(orderValue) =>
          ZQuery.fromZIO(
            ((data.Order.customer <>: data.Customer.orders) &
              (data.Order.items <>: (data.Item.product & data.Item.price)) &
              data.Order.shipment).toZIO(orderValue)
          ).map {
            case (customerValue, customerOrdersValue, linesValue, shipmentValue) =>
              Some(
                transformers.adminOrderView(
                  orderValue,
                  customerValue,
                  customerOrdersValue,
                  linesValue,
                  shipmentValue
                )
              )
          }
        case None =>
          ZQuery.succeed(None)
      }
  }
}
