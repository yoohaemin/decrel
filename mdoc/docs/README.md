---
lang: en-US
home: true
heroText: decrel
tagline: Declarative Relations for Scala
actionText: Start Here ->
actionLink: /guide/getting-started.html
features:
- title: Succinct
  details: "Application code purely expresses the data it wants, not how to fetch it."
- title: Optimized
  details: "Back your queries with ZQuery (ZIO), Fetch (cats-effect), or Kyo (kyo-batch) backends for optimized access to your data source."
- title: Reusable
  details: "Declare relations once, use them anywhere: business logic, API layer, or even property-based testing generators."
- title: Compile-Time Friendly
  details: "decrel uses vanilla guided implicit derivation with minimal impact on compile time. No macros."
- title: Unintrusive
  details: "decrel can be incrementally adopted in existing codebases. No rewrites needed."
- title: Domain Knowledge Documentation
  details: "The implicit domain knowledge of how domain models relate to each other is explicitly codified and checked into the codebase."
footer: MPL-2.0 Licensed | Copyright © 2022-2026 Haemin Yoo
---

# decrel

`decrel` is a Scala library for modeling relations in your domain and composing joins declaratively.

## What decrel does

What decrel does is quite simple:

- decrel turns relations in your domain into first-class composable values
- decrel preserves relation composition when lowering to `flatMap`, `map`, and `foreach`-style programs, so the same model works with ordinary monadic runtimes

The core idea is simple:

- define a relation once at the domain level
  - given an `A`, I can get `B` through relation `R`
- define an implementation for it once at the storage or client level
  - for relation `R`, given multiple `A`, return pairs of provided `A` and fetched `B`
- use the relation to fetch `B` from `A`
  - the implementation will be supplied implicitly

That shifts application code from "how do I fetch?" to "what do I fetch?"

## How this differs from traditional approaches

Traditionally, data-fetching abstractions live close to the datasource, especially around SQL. That is natural, because SQL expresses joins directly. But that approach has two recurring limits:

1. Limited composability

Even with strong query builders, it is hard to factor one large query into reusable fragments and recombine them cleanly across application boundaries.

2. Tied to one storage model

As soon as the required data spans HTTP calls, non-SQL stores, or multiple databases, the application usually falls back to manual stitching.

decrel flips the default: join composition is expressed in the application layer, not buried inside one datasource technology.

## What that gives you

1. Ordinary values

Relations are just values. No macros, no inline tricks, no special whitelist of allowed operations.

2. Datasource independence

Business logic expresses what data it wants, without caring whether it comes from SQL, HTTP, another service, or a mix of sources.

3. Escape hatches

The default execution model follows the relation structure. When a path is performance-sensitive, you can provide a dedicated optimized implementation for that specific query.

## What this looks like in application code

Relations compose directly into the shape your service needs:

::: details Domain used to infer the types below

```scala mdoc:silent
import decrel.*

case class Customer(id: String)
object Customer {
  case object order extends Relation.Many[Customer, List, Order]
}

case class Order(id: String, customerId: String)
object Order {
  case object customer extends Relation.Single[Order, Customer]
  case object items extends Relation.Many[Order, List, Item]
}

case class Item(id: String, orderId: String, productId: String, priceId: String)
object Item {
  case object product extends Relation.Single[Item, Product]
  case object price extends Relation.Single[Item, Price]
}

case class Product(id: String)
case class Price(id: String)

val customer = Customer("c-1")
val order = Order("o-1", customer.id)
val item = Item("i-1", order.id, "p-1", "pr-1")
val product = Product("p-1")
val price = Price("pr-1")
```

:::

::: details ZQuery setup used to infer the ZIO types below

```scala mdoc:silent
import decrel.reify.zquery
import zio.{Task, ZIO}

object ZioRelations extends zquery[Any] {
  implicit val orderCustomer: Proof.Single[
    Order.customer.type,
    Order,
    Nothing,
    Customer
  ] =
    implementSingleDatasource(Order.customer) { orders =>
      ZIO.succeed(orders.map(order => order -> customer))
    }

  implicit val orderItems: Proof.Many[
    Order.items.type,
    Order,
    Nothing,
    List,
    Item
  ] =
    implementManyDatasource(Order.items) { orders =>
      ZIO.succeed(orders.map(order => order -> List(item)))
    }

  implicit val itemProduct: Proof.Single[
    Item.product.type,
    Item,
    Nothing,
    Product
  ] =
    implementSingleDatasource(Item.product) { items =>
      ZIO.succeed(items.map(item => item -> product))
    }

  implicit val itemPrice: Proof.Single[
    Item.price.type,
    Item,
    Nothing,
    Price
  ] =
    implementSingleDatasource(Item.price) { items =>
      ZIO.succeed(items.map(item => item -> price))
    }

  implicit val customerOrders: Proof.Many[
    Customer.order.type,
    Customer,
    Nothing,
    List,
    Order
  ] =
    implementManyDatasource(Customer.order) { customers =>
      ZIO.succeed(customers.map(customer => customer -> List(order)))
    }
}
```

:::

::: details Fetch setup used to infer the cats-effect types below

```scala mdoc:silent
import _root_.cats.effect.IO
import _root_.cats.effect.kernel.Concurrent
import decrel.reify.fetch

object FetchRelations extends fetch[IO] {
  override protected implicit val CF: Concurrent[IO] =
    IO.asyncForIO

  implicit val orderCustomer: Proof.Single[
    Order.customer.type,
    Order,
    Customer
  ] =
    implementSingleDatasource(Order.customer) { orders =>
      IO.pure(orders.map(order => order -> customer))
    }

  implicit val orderItems: Proof.Many[
    Order.items.type,
    Order,
    List,
    Item
  ] =
    implementManyDatasource(Order.items) { orders =>
      IO.pure(orders.map(order => order -> List(item)))
    }

  implicit val itemProduct: Proof.Single[
    Item.product.type,
    Item,
    Product
  ] =
    implementSingleDatasource(Item.product) { items =>
      IO.pure(items.map(item => item -> product))
    }

  implicit val itemPrice: Proof.Single[
    Item.price.type,
    Item,
    Price
  ] =
    implementSingleDatasource(Item.price) { items =>
      IO.pure(items.map(item => item -> price))
    }

  implicit val customerOrders: Proof.Many[
    Customer.order.type,
    Customer,
    List,
    Order
  ] =
    implementManyDatasource(Customer.order) { customers =>
      IO.pure(customers.map(customer => customer -> List(order)))
    }
}
```

:::

::: code-tabs#runtime

@tab ZIO

```scala mdoc
import ZioRelations.*

def zioCheckoutView(order: Order): Task[(Customer, List[(Item, Product, Price)])] =
  (Order.customer & (Order.items <>: (Item.product & Item.price))).toZIO(order)

def zioAdminView(order: Order): Task[(Customer, List[Order])] =
  (Order.customer <>: Customer.order).toZIO(order)
```

@tab cats-effect

```scala mdoc
import FetchRelations.*

def fetchCheckoutView(order: Order): IO[(Customer, List[(Item, Product, Price)])] =
  (Order.customer & (Order.items <>: (Item.product & Item.price))).toF(order)

def fetchAdminView(order: Order): IO[(Customer, List[Order])] =
  (Order.customer <>: Customer.order).toF(order)
```

:::

That relation can then be executed efficiently by an integration module, while the service keeps expressing business intent instead of fetch plumbing.

## Quick Links

- [Why decrel](guide/README.md)
- [Production readiness](guide/production-readiness.md)
- [Getting started](guide/getting-started.md)
- [Composition of joins](guide/composition-of-joins.md)
- [Example app and dependency injection](example-app/README.md)
- [Module matrix](reference/module-matrix.md)
- [API reference](reference/api-reference.md)
