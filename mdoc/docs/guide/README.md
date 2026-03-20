---
lang: en-US
title: Why decrel
---

# Why decrel

decrel is built around one idea:

> joins are a domain concept, not just a query-planning detail.

Applications constantly need to answer questions like:

- given an `Order`, get its `Customer`
- given that `Order`, get all `OrderItem`
- for each `OrderItem`, get the `Product` and current `Price`

In ordinary application code, those joins are often spread across:

- service methods
- repository calls
- effect sequencing
- batching utilities
- cache lookups

The problem is not only performance. The larger problem is that the shape of the data you want and the steps needed to fetch it are tangled together.

## Composition of joins

decrel makes joins explicit as relations:

```scala
object Order {
  object customer extends Relation.Single[Order, Customer]
  object items extends Relation.Many[Order, List, OrderItem]
}

object OrderItem {
  object product extends Relation.Single[OrderItem, Product]
  object price extends Relation.Single[OrderItem, Price]
}
```

Once they exist, you compose them:

```scala
val checkoutView =
  Order.customer & (Order.items <>: (OrderItem.product & OrderItem.price))
```

That expression is the business-level join structure. It is reusable, testable, and independent of how the data is fetched.

## Why this is better than hand-written orchestration

The imperative alternative typically grows like this:

1. fetch one thing
2. inspect it
3. fetch dependent things
4. manually batch or parallelize the independent branches
5. add cache lookups
6. repeat the same pattern in another service

decrel keeps the service focused on the shape of the data it needs:

```scala
val checkoutView =
  Order.customer & (Order.items <>: (OrderItem.product & OrderItem.price))

service.load(orderId, checkoutView)
```

The relation itself becomes part of the application vocabulary.

## Where the payoff shows up

decrel is most useful when:

- your application repeatedly traverses the same domain graph
- batching and deduplication matter
- service code is turning into fetch choreography
- you want one description of a relation graph that can be reused in runtime code and tests

The next pages show two things in parallel:

- why this model is operationally credible
- how to use the APIs from simple relations up to custom proofs and cache-aware execution
