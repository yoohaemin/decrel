package decrel.examples.zio.ecommerce.stores.interface

import decrel.examples.zio.ecommerce.data._
import decrel.reify.zquery
import zio.Chunk

trait Proofs extends zquery[Any] {
  import Proof._

  given customerFetchProof: Single[Customer.fetch.type, Customer.Id, Error, Customer]
  given loyaltyTierFetchProof
    : Single[LoyaltyTier.fetch.type, LoyaltyTier.Id, Error, LoyaltyTier]
  given orderFetchProof: Single[Order.fetch.type, Order.Id, Error, Order]
  given productFetchProof: Single[Product.fetch.type, Product.Id, Error, Product]
  given priceFetchProof: Single[Price.fetch.type, Price.Id, Error, Price]
  given shipmentFetchProof: Single[Shipment.fetch.type, Shipment.Id, Error, Shipment]

  given orderCustomerProof: Single[Order.customer.type, Order, Error, Customer]
  given orderItemsProof: Many[Order.items.type, Order, Nothing, Chunk, Item]
  given orderShipmentProof: Optional[Order.shipment.type, Order, Error, Shipment]
  given customerLoyaltyTierProof
    : Optional[Customer.loyaltyTier.type, Customer, Error, LoyaltyTier]
  given customerOrdersProof: Many[Customer.orders.type, Customer, Nothing, Chunk, Order]
  given itemProductProof: Single[Item.product.type, Item, Error, Product]
  given itemPriceProof: Single[Item.price.type, Item, Error, Price]
}
