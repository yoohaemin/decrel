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
}
