---
lang: en-US
title: Implementing proofs
---

# Implementing proofs

Declared relations become executable when you provide proofs.

In everyday use, that usually means implementing datasource-backed proofs through an integration module.

## The common API shape

Runtime integrations expose the same core ideas:

- `implementSingleDatasource`
- `implementOptionalDatasource`
- `implementManyDatasource`
- `implementCustomDatasource`
- `contramapOneProof`
- `contramapOptionalProof`
- `contramapManyProof`

## ZIO and ZQuery

```scala
import decrel.*
import decrel.reify.zquery
import zio.*

object OrderRelations extends zquery[Any] {

  implicit val orderCustomer =
    implementSingleDatasource(Order.customer) { orders =>
      ZIO.succeed(
        orders.map(order => order -> customerIndex(order.customerId))
      )
    }

  implicit val orderItems =
    implementManyDatasource(Order.items) { orders =>
      ZIO.succeed(
        orders.map(order => order -> orderItemsIndex(order.id))
      )
    }
}
```

## cats-effect and Fetch

```scala
import cats.effect.IO
import decrel.*
import decrel.reify.fetch

object OrderRelations extends fetch[IO] {
  override protected implicit val CF = IO.asyncForIO

  implicit val orderCustomer =
    implementSingleDatasource(Order.customer) { orders =>
      IO.pure(
        orders.map(order => order -> customerIndex(order.customerId))
      )
    }
}
```

## Reusing an existing proof with `contramap`

`contramap*Proof` is useful when you already have a proof for an identifier lookup and want to reuse it for a richer input type.

```scala
implicit val fetchCustomerById =
  implementSingleDatasource(Customer.fetch) { ids =>
    ZIO.succeed(ids.map(id => id -> customerIndex(id)))
  }

implicit val orderCustomer =
  contramapOneProof(fetchCustomerById, Order.customer, _.customerId)
```

This keeps proofs small and prevents repeated lookup logic.

## When to use custom datasources

Reach for `implementCustomDatasource` when:

- you want to give a composed relation its own optimized implementation
- the natural execution strategy is not just a composition of primitive proofs
- you want a stable reusable relation API with a custom backend plan

## Lower-level proof APIs

The `Proof` types are public because advanced use cases need them. Most users should:

- start with declared relations
- use the `implement*Datasource` helpers
- use `contramap*Proof` before writing lower-level custom proofs

The [advanced reference](../reference/advanced-apis.md) covers the lower-level surfaces.
