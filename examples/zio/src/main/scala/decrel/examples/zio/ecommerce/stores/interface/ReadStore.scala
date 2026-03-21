package decrel.examples.zio.ecommerce.stores.interface

import decrel.examples.zio.ecommerce.data._
import zio.{ Chunk, IO, UIO }

trait ReadStore {
  def customers(ids: Chunk[Customer.Id]): IO[ExampleError, Chunk[(Customer.Id, Customer)]]
  def loyaltyTiers(ids: Chunk[LoyaltyTier.Id]): IO[ExampleError, Chunk[(LoyaltyTier.Id, LoyaltyTier)]]
  def orders(ids: Chunk[Order.Id]): IO[ExampleError, Chunk[(Order.Id, Order)]]
  def products(ids: Chunk[Product.Id]): IO[ExampleError, Chunk[(Product.Id, Product)]]
  def prices(ids: Chunk[Price.Id]): IO[ExampleError, Chunk[(Price.Id, Price)]]
  def shipments(ids: Chunk[Shipment.Id]): IO[ExampleError, Chunk[(Shipment.Id, Shipment)]]

  def listOrders(
    cursor: Option[Order.Id],
    maxItems: Int,
    filter: ReadStore.OrderFilter
  ): UIO[Chunk[Order]]

  def listItems(
    cursor: Option[Item.Id],
    maxItems: Int,
    filter: ReadStore.ItemFilter
  ): UIO[Chunk[Item]]

  def calls: UIO[Chunk[StoreCall]]
  def clearCalls: UIO[Unit]
}

final case class StoreCall(name: String, keys: Chunk[String])

object ReadStore {
  final case class OrderFilter(
    idsIn: Option[Chunk[Order.Id]] = None,
    customerIdsIn: Option[Chunk[Customer.Id]] = None
  )

  final case class ItemFilter(
    idsIn: Option[Chunk[Item.Id]] = None,
    orderIdsIn: Option[Chunk[Order.Id]] = None
  )
}
