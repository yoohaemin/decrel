---
lang: en-US
title: Production readiness
---

# Production readiness

decrel should be presented as production-oriented, but only with claims that can be defended.

## Evidence already present in this repository

- Published modules for multiple use cases: runtime integrations, testing integrations, and lower-level extension modules.
- Cross-building in `build.sbt` for Scala 2.13 and Scala 3.
- JVM and Scala.js support for the primary modules.
- Continuous integration across multiple Scala versions.
- Generated documentation via `mdoc`, which keeps examples tied to the actual codebase.
- Tests that explicitly cover:
  - relation composition semantics
  - batching
  - deduplication
  - caching
  - optional and many-valued traversals

## Guarantees worth emphasizing

- Relations are typed, so composition errors are surfaced at compile time.
- Independent branches can be evaluated by the integration layer in parallel.
- Repeated fetches can be deduplicated by integrations such as ZQuery and Fetch.
- Cache injection is explicit rather than hidden global state.
- The same relation graph can be reused in test data generation.

## Limits worth stating plainly

decrel is not a replacement for every data access concern.

- It does not remove the need for well-designed datasource implementations.
- It does not invent optimal backend queries if the underlying proof implementation is inefficient.
- It is strongest when your application naturally traverses connected domain data.
- Teams still need to choose appropriate effect, cache, and dependency-injection patterns.

Being explicit about these limits makes the production-readiness story stronger, not weaker.

## What to point to in code reviews or adoption discussions

When evaluating whether decrel is fit for production use, the strongest arguments are:

- the public API is small and compositional
- runtime integrations are tested for the operational behaviors that matter
- docs demonstrate both the happy path and advanced escape hatches
- the example app shows how decrel fits into ordinary application architecture
