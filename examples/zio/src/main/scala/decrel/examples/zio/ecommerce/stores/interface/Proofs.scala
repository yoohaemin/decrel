package decrel.examples.zio.ecommerce.stores.interface

import decrel.examples.zio.ecommerce.data._
import decrel.reify.zquery
import zio.Chunk

trait Proofs extends zquery[Any] {
  import Proof._

  given customerFetchProof: Single[Customer.fetch.type, Customer.Id, ExampleError, Customer]
  given loyaltyTierFetchProof
    : Single[LoyaltyTier.fetch.type, LoyaltyTier.Id, ExampleError, LoyaltyTier]
  given orderFetchProof: Single[Order.fetch.type, Order.Id, ExampleError, Order]
  given productFetchProof: Single[Product.fetch.type, Product.Id, ExampleError, Product]
  given priceFetchProof: Single[Price.fetch.type, Price.Id, ExampleError, Price]
  given shipmentFetchProof: Single[Shipment.fetch.type, Shipment.Id, ExampleError, Shipment]

  given orderCustomerProof: Single[Order.customer.type, Order, ExampleError, Customer]
  given orderItemsProof: Many[Order.items.type, Order, Nothing, Chunk, Item]
  given orderShipmentProof: Optional[Order.shipment.type, Order, ExampleError, Shipment]
  given customerLoyaltyTierProof
    : Optional[Customer.loyaltyTier.type, Customer, ExampleError, LoyaltyTier]
  given customerOrdersProof: Many[Customer.orders.type, Customer, Nothing, Chunk, Order]
  given itemProductProof: Single[Item.product.type, Item, ExampleError, Product]
  given itemPriceProof: Single[Item.price.type, Item, ExampleError, Price]
}
