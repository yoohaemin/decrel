---
lang: en-US
title: Testing
---

# Testing

decrel is useful in tests for two different reasons:

- the runtime integrations can be tested for batching, deduplication, caching, and correctness
- the testing modules let you generate data by traversing the same relation graph

## ScalaCheck

```scala
import decrel.scalacheck.gen.*
import org.scalacheck.Gen

val userGen: Gen[User] =
  Gen.const(User(User.Id("u-1"), "Ada"))

val orderWithCustomer: Gen[(Order, Customer)] =
  userGen.flatMap(_ => Gen.const(Order(Order.Id("o-1"), User.Id("u-1")))).expand(Order.customer)
```

More commonly, you will define relation-aware generators with:

- `Gen.relationSingle`
- `Gen.relationOptional`
- `Gen.relationMany`

and then use `expand`.

## ZIO Test

```scala
import decrel.ziotest.gen.*
import zio.test.Gen

val userGen: Gen[Any, User] =
  Gen.const(User(User.Id("u-1"), "Ada"))
```

The ZIO Test integration mirrors the same mental model.

## Service-level testing

The strongest service tests do not assert only final values. They also assert operational behavior where relevant:

- repeated branches are deduplicated
- many-valued traversals are batched
- seeded caches skip redundant lookups

The runtime integration specs in this repository are good examples of what to verify when introducing new proofs.

## Recommended testing split

- unit test declared relations and proof wiring where the type-level shape is non-obvious
- integration test key relation graphs for batching and cache behavior
- use relation-driven generators for richer domain test data
