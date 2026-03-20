---
lang: en-US
title: Getting started
---

# Getting started

decrel is published for Scala 2.13 and 3, and supports JVM and JS for the primary modules.

## Picking modules

Use the smallest module set that matches your application.

| Module | Use when |
| --- | --- |
| `decrel-core` | You want relation definitions and composition only. |
| `decrel-zquery` | You execute relations with ZIO and ZQuery. |
| `decrel-fetch` | You execute relations with cats-effect and Fetch. |
| `decrel-scalacheck` | You want relation-driven generators in ScalaCheck. |
| `decrel-ziotest` | You want relation-driven generators in ZIO Test. |
| `decrel-cats` | You are building your own monadic integration on top of `cats.Monad`. |
| `decrel-kyo` | You want the generic Kyo integration layer. |
| `decrel-kyo-batch` | You want batched execution with Kyo `Batch`. |

## Dependency coordinates

### sbt

```scala
"com.yoohaemin" %% "decrel-core"       % "@RELEASEVERSION@"
"com.yoohaemin" %% "decrel-zquery"     % "@RELEASEVERSION@"
"com.yoohaemin" %% "decrel-fetch"      % "@RELEASEVERSION@"
"com.yoohaemin" %% "decrel-scalacheck" % "@RELEASEVERSION@" % Test
"com.yoohaemin" %% "decrel-ziotest"    % "@RELEASEVERSION@" % Test
"com.yoohaemin" %% "decrel-cats"       % "@RELEASEVERSION@"
"com.yoohaemin" %% "decrel-kyo"        % "@RELEASEVERSION@"
"com.yoohaemin" %% "decrel-kyo-batch"  % "@RELEASEVERSION@"
```

### mill

```scala
ivy"com.yoohaemin::decrel-core:@RELEASEVERSION@"
ivy"com.yoohaemin::decrel-zquery:@RELEASEVERSION@"
ivy"com.yoohaemin::decrel-fetch:@RELEASEVERSION@"
ivy"com.yoohaemin::decrel-scalacheck:@RELEASEVERSION@"
ivy"com.yoohaemin::decrel-ziotest:@RELEASEVERSION@"
ivy"com.yoohaemin::decrel-cats:@RELEASEVERSION@"
ivy"com.yoohaemin::decrel-kyo:@RELEASEVERSION@"
ivy"com.yoohaemin::decrel-kyo-batch:@RELEASEVERSION@"
```

## Minimal mental model

1. Declare relations in the domain.
2. Compose them into the shape your service needs.
3. Provide proofs that explain how to fetch each declared relation.
4. Execute the relation with the integration module.

## Minimal example with ZIO

```scala mdoc:silent
import decrel.*
import decrel.reify.zquery
import zio.*

case class User(id: User.Id, name: String)
object User {
  type Id = String
}

case class Order(id: Order.Id, userId: User.Id)
object Order {
  type Id = String

  case object fetch extends Relation.Single[Order.Id, Order]
  case object user extends Relation.Single[Order, User]
}

object OrderRelations extends zquery[Any] {
  private val users =
    Map("u-1" -> User("u-1", "Ada"))
  private val orders =
    Map("o-1" -> Order("o-1", "u-1"))

  implicit val fetchOrder: Proof.Single[
    Order.fetch.type & Relation.Single[Order.Id, Order],
    Order.Id,
    Nothing,
    Order
  ] =
    implementSingleDatasource(Order.fetch) { ids =>
      ZIO.succeed(ids.map(id => id -> orders(id)))
    }

  implicit val orderUser: Proof.Single[
    Order.user.type & Relation.Single[Order, User],
    Order,
    Nothing,
    User
  ] =
    implementSingleDatasource(Order.user) { ordersChunk =>
      ZIO.succeed(ordersChunk.map(order => order -> users(order.userId)))
    }
}

import OrderRelations.*

val relation = Order.fetch <>: Order.user
val program: ZIO[Any, Nothing, (Order, User)] =
  relation.toZIO("o-1")
```

## Minimal example with cats-effect and Fetch

```scala
import cats.effect.IO
import decrel.*
import decrel.reify.fetch

case class User(id: User.Id, name: String)
object User {
  final case class Id(value: String)
}

case class Order(id: Order.Id, userId: User.Id)
object Order {
  final case class Id(value: String)

  case object fetch extends Relation.Single[Order.Id, Order]
  case object user extends Relation.Single[Order, User]
}

object OrderRelations extends fetch[IO] {
  override protected implicit val CF = IO.asyncForIO

  private val users =
    Map(User.Id("u-1") -> User(User.Id("u-1"), "Ada"))
  private val orders =
    Map(Order.Id("o-1") -> Order(Order.Id("o-1"), User.Id("u-1")))

  implicit val fetchOrder =
    implementSingleDatasource(Order.fetch) { ids =>
      IO.pure(ids.map(id => id -> orders(id)))
    }

  implicit val orderUser =
    implementSingleDatasource(Order.user) { ordersList =>
      IO.pure(ordersList.map(order => order -> users(order.userId)))
    }
}

import OrderRelations.*

val relation = Order.fetch <>: Order.user
val program: IO[(Order, User)] =
  relation.toF(Order.Id("o-1"))
```

From here, move on to:

- [Defining relations](defining-relations.md)
- [Composition of joins](composition-of-joins.md)
- [Implementing proofs](implementing-proofs.md)
