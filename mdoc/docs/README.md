---
lang: en-US
home: true
heroText: decrel
tagline: Compose joins declaratively, then execute them efficiently.
actionText: Start Here ->
actionLink: /guide/getting-started.html
features:
- title: Composition of Joins
  details: Model joins and traversals as reusable relations, then compose them with operators instead of hand-writing fetch choreography in services.
- title: Production-Focused
  details: decrel supports Scala 2.13 and 3, JVM and JS, ships published modules, and is tested for batching, deduplication, caching, and composition semantics.
- title: App-Level Payoff
  details: Relations keep business logic centered on the data shape you want, while integrations such as ZQuery and Fetch handle batching and parallelism.
- title: Multiple Integrations
  details: Use decrel with ZIO, cats-effect and Fetch, ScalaCheck, ZIO Test, Kyo, or custom monadic integrations.
- title: Advanced Control
  details: Reach for proofs, custom relations, contramap helpers, and cache injection when the simple path is not enough.
- title: Precise Reference
  details: Narrative guides live next to generated Scaladoc so you can move from concept to exact API quickly.
footer: MPL-2.0 Licensed | Copyright © 2022-2026 Haemin Yoo
---

# decrel

`decrel` is a Scala library for modeling relations in your domain and composing joins declaratively.

The core idea is simple:

- define a relation once
- compose it with other relations
- execute the composition with batching, caching, and parallelism handled by the integration layer

That shifts application code from "how do I orchestrate these fetches?" to "what connected data shape do I need here?"

## The problem decrel solves

Most applications repeatedly solve the same problem:

- start from one entity
- follow several joins
- keep optional or one-to-many branches straight
- avoid N+1 queries
- preserve readable service code

Without a dedicated abstraction, that logic leaks into services, repositories, and controllers. The result is usually a mix of nested `flatMap`, ad hoc batching, and duplicated fetch orchestration.

decrel treats those joins as first-class values.

## Why this is useful in application code

Relations compose directly into the shape your service needs:

```scala
val checkoutView =
  Order.customer & (Order.items <>: (OrderItem.product & OrderItem.price))
```

That relation can then be executed efficiently by an integration module, while the service keeps expressing business intent instead of fetch plumbing.

## Quick Links

- [Why decrel](guide/README.md)
- [Production readiness](guide/production-readiness.md)
- [Getting started](guide/getting-started.md)
- [Composition of joins](guide/composition-of-joins.md)
- [Example app and dependency injection](example-app/README.md)
- [Module matrix](reference/module-matrix.md)
- [API reference](reference/api-reference.md)

## What you will find in these docs

- a conceptual explanation of "composition of joins"
- evidence-backed production-readiness guidance
- tutorials for defining relations and implementing proofs
- cache, custom relation, and contramap coverage
- an example app shown in both ZIO and cats-effect styles
- generated Scaladoc for the public modules
