package decrel.examples.zio.ecommerce.graphql.protocol

import caliban.schema.Schema
import zio.Chunk
import zio.query.ZQuery

type TaskQuery[+A] = ZQuery[Any, Throwable, A]

final case class LoyaltyTier(
  id: String,
  label: String,
  discountPercent: Int
) derives Schema.SemiAuto

final case class Price(
  id: String,
  amount: BigDecimal,
  currency: String
) derives Schema.SemiAuto

final case class Product(
  id: String,
  sku: String,
  name: String
) derives Schema.SemiAuto

final case class Shipment(
  id: String,
  carrier: String,
  trackingNumber: String,
  status: String
) derives Schema.SemiAuto

final case class Item(
  id: String,
  quantity: Int,
  product: TaskQuery[Product],
  price: TaskQuery[Price]
) derives Schema.SemiAuto

final case class Order(
  id: String,
  reference: String,
  customer: TaskQuery[Customer],
  items: TaskQuery[Chunk[Item]],
  shipment: TaskQuery[Option[Shipment]]
) derives Schema.SemiAuto

final case class Customer(
  id: String,
  name: String,
  loyaltyTier: TaskQuery[Option[LoyaltyTier]],
  orders: TaskQuery[Chunk[Order]]
) derives Schema.SemiAuto
