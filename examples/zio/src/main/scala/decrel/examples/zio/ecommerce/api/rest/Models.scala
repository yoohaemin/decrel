package decrel.examples.zio.ecommerce.api.rest

import decrel.examples.zio.ecommerce.data
import zio.Chunk
import zio.json.{ DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder }

final case class Customer(
  id: String,
  name: String,
  loyaltyTierId: Option[String]
)

object Customer {
  given JsonEncoder[Customer] = DeriveJsonEncoder.gen
  given JsonDecoder[Customer] = DeriveJsonDecoder.gen

  def from(value: data.Customer): Customer =
    Customer(value.id.value, value.name, value.loyaltyTierId.map(_.value))
}

final case class Shipment(
  id: String,
  carrier: String,
  trackingNumber: String,
  status: String
)

object Shipment {
  given JsonEncoder[Shipment] = DeriveJsonEncoder.gen
  given JsonDecoder[Shipment] = DeriveJsonDecoder.gen

  def from(value: data.Shipment): Shipment =
    Shipment(value.id.value, value.carrier, value.trackingNumber, value.status)
}

final case class Order(
  id: String,
  customerId: String,
  shipmentId: Option[String],
  reference: String
)

object Order {
  given JsonEncoder[Order] = DeriveJsonEncoder.gen
  given JsonDecoder[Order] = DeriveJsonDecoder.gen

  def from(value: data.Order): Order =
    Order(value.id.value, value.customerId.value, value.shipmentId.map(_.value), value.reference)
}

final case class Product(
  id: String,
  sku: String,
  name: String
)

object Product {
  given JsonEncoder[Product] = DeriveJsonEncoder.gen
  given JsonDecoder[Product] = DeriveJsonDecoder.gen

  def from(value: data.Product): Product =
    Product(value.id.value, value.sku, value.name)
}

final case class Price(
  id: String,
  amount: BigDecimal,
  currency: String
)

object Price {
  given JsonEncoder[Price] = DeriveJsonEncoder.gen
  given JsonDecoder[Price] = DeriveJsonDecoder.gen

  def from(value: data.Price): Price =
    Price(value.id.value, value.amount, value.currency)
}

final case class CheckoutLine(
  itemId: String,
  quantity: Int,
  product: Product,
  price: Price
)

object CheckoutLine {
  given JsonEncoder[CheckoutLine] = DeriveJsonEncoder.gen
  given JsonDecoder[CheckoutLine] = DeriveJsonDecoder.gen

  def from(item: data.Item, product: data.Product, price: data.Price): CheckoutLine =
    CheckoutLine(item.id.value, item.quantity, Product.from(product), Price.from(price))
}

final case class CheckoutView(
  order: Order,
  customer: Customer,
  loyaltyTier: Option[String],
  lines: Chunk[CheckoutLine],
  shipment: Option[Shipment]
)

object CheckoutView {
  given JsonEncoder[CheckoutView] = DeriveJsonEncoder.gen
  given JsonDecoder[CheckoutView] = DeriveJsonDecoder.gen

  def from(
    order: data.Order,
    customer: data.Customer,
    loyaltyTier: Option[data.LoyaltyTier],
    lines: Chunk[(data.Item, data.Product, data.Price)],
    shipment: Option[data.Shipment]
  ): CheckoutView =
    CheckoutView(
      order = Order.from(order),
      customer = Customer.from(customer),
      loyaltyTier = loyaltyTier.map(_.label),
      lines = lines.map { case (item, product, price) => CheckoutLine.from(item, product, price) },
      shipment = shipment.map(Shipment.from)
    )
}

final case class AdminLine(
  itemId: String,
  quantity: Int,
  product: Product,
  price: Price
)

object AdminLine {
  given JsonEncoder[AdminLine] = DeriveJsonEncoder.gen
  given JsonDecoder[AdminLine] = DeriveJsonDecoder.gen

  def from(item: data.Item, product: data.Product, price: data.Price): AdminLine =
    AdminLine(item.id.value, item.quantity, Product.from(product), Price.from(price))
}

final case class AdminOrderView(
  order: Order,
  customer: Customer,
  customerOrders: Chunk[Order],
  lines: Chunk[AdminLine],
  shipment: Option[Shipment]
)

object AdminOrderView {
  given JsonEncoder[AdminOrderView] = DeriveJsonEncoder.gen
  given JsonDecoder[AdminOrderView] = DeriveJsonDecoder.gen

  def from(
    order: data.Order,
    customer: data.Customer,
    customerOrders: Chunk[data.Order],
    lines: Chunk[(data.Item, data.Product, data.Price)],
    shipment: Option[data.Shipment]
  ): AdminOrderView =
    AdminOrderView(
      order = Order.from(order),
      customer = Customer.from(customer),
      customerOrders = customerOrders.map(Order.from),
      lines = lines.map { case (item, product, price) => AdminLine.from(item, product, price) },
      shipment = shipment.map(Shipment.from)
    )
}
