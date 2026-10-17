# Kyo Batch interpreter

`kyoBatch[Eff]` builds a `KyoBatchQuery[Eff, A]` before executing it. Independent branches and bulk inputs contribute requests to the same dependency stage. The runner submits that stage to Kyo's `Batch.run`, which groups requests by datasource and deduplicates keys. Dependent stages run after their inputs are available.

Output positions, duplicate inputs, optional absence, and nested collection boundaries are preserved even when different datasources complete in a different order. Empty traversals do not call a datasource. Deduplication applies within a stage; this is not a cross-stage or persistent result cache. The interpreter does not promise concurrent execution of different datasource effects.

## Execution and migration

`relation.startingFrom(input)` and `relation.startingFromMany(inputs)` continue to return Kyo effects (`Out < Eff` and `Coll[Out] < Eff`). Datasource callback signatures are unchanged.

The low-level execution model changes: `proof.reify.apply(input)` and `applyMultiple(inputs)` now return a reusable `KyoBatchQuery`. Call `.run` to obtain the Kyo effect, or compose query plans with `.map` and `.flatMap` before running them. Reusing a query does not reuse fetched results.

Custom `ReifiedRelation` implementations should return `Access[A]` and use `KyoBatchQuery.lift(effect)` for an ordinary Kyo effect, or compose the plans from registered datasource proofs. `lift` is by-name, so its expression is evaluated when the plan runs. Use the datasource constructors when requests should batch by key. `kyoBatch` no longer extends `kyoGeneric`, whose direct-effect interpretation remains unchanged.

## Implementation

A query step is either complete or contains pending requests plus a typed continuation. Zipping pending steps pairs their immutable states. Each request returns an update for its own subtree, allowing the runner to reconstruct results without relying on the order in which Kyo groups sources. Traversal builds a balanced tree so a large input collection does not create a linear-depth zip chain.

The runner advances one stage at a time. Kyo remains responsible for executing datasource effects and batching and deduplicating source requests. No shared mutable builders or result slots are retained in query plans.
