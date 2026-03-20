---
lang: en-US
title: Defining relations
---

# Defining relations

Relations live with the domain model. They describe the shape of valid joins in your application.

## The three declared relation kinds

### `Relation.Single`

Use this when there is exactly one target value.

```scala
object Order {
  object customer extends Relation.Single[Order, Customer]
}
```

### `Relation.Optional`

Use this when the target may be absent.

```scala
object User {
  object activeSubscription extends Relation.Optional[User, Subscription]
}
```

### `Relation.Many`

Use this when one source value maps to multiple target values.

```scala
object Order {
  object items extends Relation.Many[Order, List, OrderItem]
}
```

## `Relation.Self`

`Self` is the identity relation. It is useful when you want to keep the current node while zipping in more data.

```scala
object Order {
  object self extends Relation.Self[Order]
}

val orderWithCustomer = Order.self & Order.customer
```

## Best practices for declaration

- Put declared relations next to the model they start from.
- Name them in domain language, not datasource language.
- Keep each declared relation narrow and obvious.
- Build larger traversals by composition instead of declaring giant all-in-one relations.

## Custom relations

Sometimes you want to treat a composed relation as its own reusable value.

```scala
val checkoutView =
  (Order.customer & Order.items).customImpl
```

`customImpl` turns an existing relation value into `Relation.Custom[...]`, which can then receive its own proof or datasource implementation.

## The real goal of relation declarations

A declared relation is not yet executable. It is a typed join edge in your domain graph.

That separation is important:

- the domain declares what can be joined
- proofs declare how to realize those joins in a specific runtime

The next page covers how those declared relations combine into larger traversals.
