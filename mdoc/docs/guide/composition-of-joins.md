---
lang: en-US
title: Composition of joins
---

# Composition of joins

This is the conceptual center of decrel.

You define small relation edges, then compose them into the exact data shape your application code needs.

## Parallel composition with `&`

Use `&` when two relations start from the same input and can be fetched independently.

```scala
val orderSummary =
  Order.customer & Order.items
```

That reads as:

- start from `Order`
- get its `Customer`
- also get its `OrderItem` collection

The output becomes a tuple.

## Sequential composition with `>>:`

Use `>>:` when you want to follow one relation into the next relation's input.

```scala
val orderProducts =
  Order.items >>: OrderItem.product
```

That reads as:

- start from `Order`
- follow `items`
- from each `OrderItem`, follow `product`

`>>:` keeps only the downstream result.

## Sequential composition with accumulation via `<>:`

Use `<>:` when you want the downstream result and also want to keep the intermediate node in the output.

```scala
val orderItemsWithProducts =
  Order.items <>: OrderItem.product
```

This is the operator that most directly expresses "compose joins, but keep the useful shape."

It is often the best fit for application services because it preserves context.

## Example: app-level payoff

```scala
val checkoutView =
  Order.customer & (Order.items <>: (OrderItem.product & OrderItem.price))
```

This relation says exactly what a checkout service needs:

- the customer
- each order item
- the product and price for each item

The service can work with that result directly instead of manually orchestrating the joins.

## Optional and many-valued branches

Composition respects relation cardinality:

- `Single` followed by `Single` stays direct
- `Optional` propagates optionality
- `Many` propagates collection shape

The result is a traversal algebra that mirrors the join structure of the domain.

## Operator precedence

`<>:` and `>>:` share precedence. Use parentheses when a composition tree is not immediately obvious.

```scala
val relation =
  Order.customer & (Order.items <>: (OrderItem.product & OrderItem.price))
```

Parentheses are cheap. Ambiguity in docs is not.

## Practical rule of thumb

- use `&` for sibling branches
- use `>>:` when you only care about the final target
- use `<>:` when you want the intermediate node retained in the output
