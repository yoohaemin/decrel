package decrel.examples.zio.ecommerce.graphql.transformers

import decrel.examples.zio.ecommerce.data
import decrel.examples.zio.ecommerce.graphql.protocol
import decrel.examples.zio.ecommerce.stores.interface.Proofs
import decrel.syntax._
import io.scalaland.chimney.dsl.*
import zio.URLayer
import zio.ZLayer

object Transformers {
  val live: URLayer[Proofs, Transformers] =
    ZLayer.derive[Transformers]
}

final class Transformers(proofs: Proofs) {
  import proofs.given

  def loyaltyTier(value: data.LoyaltyTier): protocol.LoyaltyTier =
    value
      .into[protocol.LoyaltyTier]
      .withFieldComputed(_.id, _.id.value)
      .transform

  def price(value: data.Price): protocol.Price =
    value
      .into[protocol.Price]
      .withFieldComputed(_.id, _.id.value)
      .transform

  def product(value: data.Product): protocol.Product =
    value
      .into[protocol.Product]
      .withFieldComputed(_.id, _.id.value)
      .transform

  def shipment(value: data.Shipment): protocol.Shipment =
    value
      .into[protocol.Shipment]
      .withFieldComputed(_.id, _.id.value)
      .transform

  def item(value: data.Item): protocol.Item =
    value
      .into[protocol.Item]
      .withFieldComputed(_.id, _.id.value)
      .withFieldComputed(_.product, item => data.Item.product.toQuery(item).map(product))
      .withFieldComputed(_.price, item => data.Item.price.toQuery(item).map(price))
      .transform

  def order(value: data.Order): protocol.Order =
    value
      .into[protocol.Order]
      .withFieldComputed(_.id, _.id.value)
      .withFieldComputed(_.customer, order => data.Order.customer.toQuery(order).map(customer))
      .withFieldComputed(_.items, order => data.Order.items.toQuery(order).map(_.map(item)))
      .withFieldComputed(_.shipment, order => data.Order.shipment.toQuery(order).map(_.map(shipment)))
      .transform

  def customer(value: data.Customer): protocol.Customer =
    value
      .into[protocol.Customer]
      .withFieldComputed(_.id, _.id.value)
      .withFieldComputed(
        _.loyaltyTier,
        customer => data.Customer.loyaltyTier.toQuery(customer).map(_.map(loyaltyTier))
      )
      .withFieldComputed(_.orders, customer => data.Customer.orders.toQuery(customer).map(_.map(order)))
      .transform

  def checkoutLine(item: data.Item, productValue: data.Product, priceValue: data.Price): protocol.CheckoutLine =
    item
      .into[protocol.CheckoutLine]
      .withFieldComputed(_.itemId, _.id.value)
      .withFieldComputed(_.product, _ => product(productValue))
      .withFieldComputed(_.price, _ => price(priceValue))
      .transform

  def checkoutView(
    orderValue: data.Order,
    customerValue: data.Customer,
    loyaltyTierValue: Option[data.LoyaltyTier],
    linesValue: zio.Chunk[(data.Item, data.Product, data.Price)],
    shipmentValue: Option[data.Shipment]
  ): protocol.CheckoutView =
    orderValue
      .into[protocol.CheckoutView]
      .withFieldComputed(_.orderId, _.id.value)
      .withFieldComputed(_.customer, _ => customer(customerValue))
      .withFieldComputed(_.loyaltyTier, _ => loyaltyTierValue.map(loyaltyTier))
      .withFieldComputed(_.lines, _ => linesValue.map { case (item, productValue, priceValue) =>
        checkoutLine(item, productValue, priceValue)
      })
      .withFieldComputed(_.shipment, _ => shipmentValue.map(shipment))
      .transform

  def adminLine(item: data.Item, productValue: data.Product, priceValue: data.Price): protocol.AdminLine =
    item
      .into[protocol.AdminLine]
      .withFieldComputed(_.itemId, _.id.value)
      .withFieldComputed(_.product, _ => product(productValue))
      .withFieldComputed(_.price, _ => price(priceValue))
      .transform

  def adminOrderView(
    orderValue: data.Order,
    customerValue: data.Customer,
    customerOrdersValue: zio.Chunk[data.Order],
    linesValue: zio.Chunk[(data.Item, data.Product, data.Price)],
    shipmentValue: Option[data.Shipment]
  ): protocol.AdminOrderView =
    orderValue
      .into[protocol.AdminOrderView]
      .withFieldComputed(_.orderId, _.id.value)
      .withFieldComputed(_.customer, _ => customer(customerValue))
      .withFieldComputed(_.customerOrders, _ => customerOrdersValue.map(order))
      .withFieldComputed(_.lines, _ => linesValue.map { case (item, productValue, priceValue) =>
        adminLine(item, productValue, priceValue)
      })
      .withFieldComputed(_.shipment, _ => shipmentValue.map(shipment))
      .transform
}
