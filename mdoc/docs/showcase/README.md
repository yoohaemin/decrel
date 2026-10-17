---
lang: en-US
title: Showcase
---

# E-Commerce Order Graph

A customer history combines orders, shipments, order items, and product details. This complete in-memory example preserves the same shape a batch database implementation would return.

## Domain and reusable expressions

Keep the inferred expression types in `Queries`. The separate `shape` value checks the public output type without erasing the executable AST.

```scala mdoc:silent
import decrel.*
import zio.*
case class Customer(id: String, name: String)
object Customer {
  case object self extends Relation.Self[Customer]
  case object fetch extends Relation.Single[String, Customer]
  case object orders extends Relation.Many[Customer, Chunk, Order]
}
case class Order(id: String, customerId: String)
object Order {
  case object self extends Relation.Self[Order]
  case object items extends Relation.Many[Order, Chunk, OrderItem]
  case object shipment extends Relation.Optional[Order, Shipment]
}
case class OrderItem(orderId: String, productId: String, quantity: Int)
object OrderItem {
  case object product extends Relation.Single[OrderItem, Product]
}
case class Product(id: String, name: String, priceCents: Int)
object Product {
  case object fetch extends Relation.Single[String, Product]
}
case class Shipment(orderId: String, trackingNumber: String)
object Queries {
  val itemsWithProduct = Order.items <>: OrderItem.product
  val fullSummary = Order.self & Order.shipment & itemsWithProduct
  val orderHistory = Customer.fetch <>: (Customer.orders >>: fullSummary)
  val shape: Relation[String, (Customer, Chunk[(Order, Option[Shipment], Chunk[(OrderItem, Product)])])] = orderHistory
}
```

## Batch datasource implementation

```scala mdoc:silent
object Store extends decrel.reify.zquery[Any] {
  val customers = Map("cust-1" -> Customer("cust-1", "Alice"))
  val orders = Chunk(Order("order-1", "cust-1"), Order("order-2", "cust-1"))
  val items = Chunk(OrderItem("order-1", "product-1", 2), OrderItem("order-2", "product-1", 1))
  val products = Map("product-1" -> Product("product-1", "Keyboard", 5000))
  val shipments = Chunk(Shipment("order-1", "tracking-1"))
  implicit val customerProof: Proof.Single[Customer.fetch.type, String, Throwable, Customer] =
    implementSingleDatasource(Customer.fetch)(ids => ZIO.attempt(ids.map(id => id -> customers(id))))
  implicit val ordersProof: Proof.Many[Customer.orders.type, Customer, Nothing, Chunk, Order] =
    implementManyDatasource(Customer.orders)(requested => ZIO.succeed(requested.map(c => c -> orders.filter(_.customerId == c.id))))
  implicit val itemsProof: Proof.Many[Order.items.type, Order, Nothing, Chunk, OrderItem] =
    implementManyDatasource(Order.items)(requested => ZIO.succeed(requested.map(o => o -> items.filter(_.orderId == o.id))))
  implicit val shipmentProof: Proof.Optional[Order.shipment.type, Order, Nothing, Shipment] =
    implementOptionalDatasource(Order.shipment)(requested => ZIO.succeed(requested.map(o => o -> shipments.find(_.orderId == o.id))))
  val productProof: Proof.Single[Product.fetch.type, String, Throwable, Product] =
    implementSingleDatasource(Product.fetch)(ids => ZIO.attempt(ids.map(id => id -> products(id))))
  implicit val itemProductProof: Proof.Single[OrderItem.product.type, OrderItem, Throwable, Product] =
    contramapOneProof(productProof, OrderItem.product, (item: OrderItem) => item.productId)
}
import Store.*
val history = Queries.orderHistory.startingFrom("cust-1")
```

## What happens at runtime?

With ZQuery, complete batch callbacks, and no backend chunk-size limits, a history with 20 orders and 100 items referencing 35 products has these dependency stages:

1. Fetch the customer: one datasource call.
2. Fetch that customer's orders: one call.
3. Fetch shipments and items for all orders: two independent batched calls.
4. Fetch the 35 distinct product IDs: one batched call.

That is **five datasource calls across four dependency stages**, not four calls. Backend pagination, batch limits, retries, or callbacks that perform per-key queries can increase actual database/network calls. Parallel branches also do not imply all stages are concurrent.

The product proof is adapted by product ID, so multiple order items can share the primary product datasource's cache and deduplication. Each returned product is still associated with its original order-item position.
