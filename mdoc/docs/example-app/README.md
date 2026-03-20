---
lang: en-US
title: Example app
---

# Example app and dependency injection

A good decrel example app should prove two things:

- relation composition directly simplifies application code
- decrel fits inside normal dependency-injection structure instead of taking over the architecture

## Domain split

Keep these concerns separate:

- `domain`: entities and declared relations
- `infra`: datasource clients, caches, and proof implementations
- `services`: business logic expressed in terms of relations
- `app`: dependency wiring

That separation keeps relations reusable and avoids smuggling infrastructure into the domain model.

## Shared domain

Use one realistic domain across both integration tracks. A checkout-style domain works well:

- `Order`
- `Customer`
- `OrderItem`
- `Product`
- `Price`

It naturally demonstrates:

- one-to-one joins
- one-to-many joins
- optional data
- mixed sequential and parallel composition

## Service code

This is the kind of application code decrel is meant to improve:

```scala
val checkoutView =
  Order.customer & (Order.items <>: (OrderItem.product & OrderItem.price))
```

A service can depend on that relation graph and its executor instead of orchestrating several repositories.

## ZIO + ZLayer track

Recommended structure:

- `OrderRelationsLive` depends on repositories, clients, and cache services
- it extends `decrel.reify.zquery[...]`
- it exposes implicit proofs
- a `ZLayer` wires the concrete implementation
- services depend on the relation module or on methods built on top of it

This keeps proofs close to infrastructure while letting services stay declarative.

## cats-effect + Resource track

Recommended structure:

- `OrderRelationsLive[F[_]]` takes its repositories and clients in the constructor
- it extends `decrel.reify.fetch[F]`
- a `Resource[F, OrderRelationsLive[F]]` wires external clients and caches
- services receive the relations module as a dependency

This mirrors the same architecture as the ZIO variant without forcing a framework-specific style.

## Cache and custom relation placement

- keep primitive relation proofs in the infra module
- keep named custom relations near the service or feature that owns the view
- inject caches at request boundaries or in the runtime integration layer, not in the domain

## What the example app should demonstrate

- primitive relation declarations in the domain
- proof reuse with `contramap`
- one seeded cache example
- one custom relation with an optimized implementation
- a service returning a view object without manual fetch choreography

If the example app communicates those points clearly, it will do more to prove decrel's value than a large generic showcase.
