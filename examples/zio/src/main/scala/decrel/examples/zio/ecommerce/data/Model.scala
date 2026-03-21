package decrel.examples.zio.ecommerce.data

import decrel.Relation
import zio.Chunk

final case class Customer(
  id: Customer.Id,
  name: String,
  loyaltyTierId: Option[LoyaltyTier.Id]
)

object Customer {
  final case class Id(value: String) extends AnyVal

  case object fetch extends Relation.Single[Id, Customer]
  case object loyaltyTier extends Relation.Optional[Customer, LoyaltyTier]
  case object orders extends Relation.Many[Customer, Chunk, Order]
}

final case class LoyaltyTier(
  id: LoyaltyTier.Id,
  label: String,
  discountPercent: Int
)

object LoyaltyTier {
  final case class Id(value: String) extends AnyVal

  case object fetch extends Relation.Single[Id, LoyaltyTier]
}

final case class Order(
  id: Order.Id,
  customerId: Customer.Id,
  shipmentId: Option[Shipment.Id],
  reference: String
)

object Order {
  final case class Id(value: String) extends AnyVal

  case object fetch extends Relation.Single[Id, Order]
  case object customer extends Relation.Single[Order, Customer]
  case object items extends Relation.Many[Order, Chunk, Item]
  case object shipment extends Relation.Optional[Order, Shipment]
}

final case class Item(
  id: Item.Id,
  orderId: Order.Id,
  productId: Product.Id,
  priceId: Price.Id,
  quantity: Int
)

object Item {
  final case class Id(value: String) extends AnyVal

  case object product extends Relation.Single[Item, Product]
  case object price extends Relation.Single[Item, Price]
}

final case class Product(
  id: Product.Id,
  sku: String,
  name: String
)

object Product {
  final case class Id(value: String) extends AnyVal

  case object fetch extends Relation.Single[Id, Product]
}

final case class Price(
  id: Price.Id,
  amount: BigDecimal,
  currency: String
)

object Price {
  final case class Id(value: String) extends AnyVal

  case object fetch extends Relation.Single[Id, Price]
}

final case class Shipment(
  id: Shipment.Id,
  carrier: String,
  trackingNumber: String,
  status: String
)

object Shipment {
  final case class Id(value: String) extends AnyVal

  case object fetch extends Relation.Single[Id, Shipment]
}
