---
lang: en-US
title: Module matrix
---

# Module matrix

| Module | Purpose | Typical user |
| --- | --- | --- |
| `decrel-core` | Relation declarations and composition | Any decrel user |
| `decrel-zquery` | ZIO + ZQuery execution | ZIO applications |
| `decrel-fetch` | cats-effect + Fetch execution | cats-effect applications |
| `decrel-scalacheck` | Relation-driven ScalaCheck generators | Test suites using ScalaCheck |
| `decrel-ziotest` | Relation-driven ZIO Test generators | Test suites using ZIO Test |
| `decrel-cats` | Base for custom `F[_]` integrations | Library or framework authors |
| `decrel-kyo` | Generic Kyo integration | Kyo users |
| `decrel-kyo-batch` | Kyo integration with `Batch` | Kyo users who want batching |

## Choosing quickly

- If you use ZIO in production code, start with `decrel-zquery`.
- If you use cats-effect in production code, start with `decrel-fetch`.
- If you only need the domain relation algebra in a shared module, `decrel-core` is enough.
- If you need test-data traversal, add the matching testing module on top.

## Advanced modules

`decrel-cats`, `decrel-kyo`, and `decrel-kyo-batch` are public and supported, but they are not the primary onboarding path.

Treat them as:

- integration modules if they match your stack
- extension points if you are building a custom execution model
