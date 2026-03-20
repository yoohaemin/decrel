---
lang: en-US
title: Cache and custom relations
---

# Cache and custom relations

decrel exposes caching as an explicit execution concern.

That matters because cache behavior is part of correctness and performance, and it should be visible in the API.

## Passing a cache explicitly

Runtime integrations expose overloads that accept a `Cache`.

### ZIO

```scala
val cache = Cache.empty
  .add(Book.fetch, book.id, book)

val relation = Book.currentRental <>: Rental.book

val program =
  relation.toZIO(book, cache)
```

### Fetch

```scala
val cache = Cache.empty
  .add(Book.fetch, book.id, book)

val relation = Book.currentRental <>: Rental.book

val program =
  relation.toF(book, cache)
```

Use this when:

- a request already has known values
- you want to seed a cache from an upstream layer
- you need deterministic cache behavior in tests

## Custom relations

`customImpl` lets you take an existing relation value and promote it into a custom relation.

```scala
val checkoutView =
  (Order.customer & (Order.items <>: OrderItem.product)).customImpl
```

That custom relation can then receive a dedicated proof or datasource implementation.

## Why custom relations matter

Custom relations are the escape hatch for cases where the naive composition is not the implementation you want.

Typical examples:

- one backend endpoint already returns the whole joined shape
- you want to reuse a named view across the codebase
- you need a hand-optimized plan for a frequently requested traversal

## Rule of thumb

- use declared relations for domain edges
- compose them freely for most application logic
- introduce custom relations when a composed view deserves its own stable name or optimized implementation
