# Kyo Batch interpreter

`kyoBatch[Eff]` builds a `KyoBatchQuery[Eff, A]` before executing it. Independent branches and bulk inputs contribute requests to the same dependency stage. The runner submits that stage to Kyo's `Batch.run`, which groups requests by datasource and deduplicates keys. Dependent stages run after their inputs are available.

Output positions, duplicate inputs, optional absence, and nested collection boundaries are preserved even when different datasources complete in a different order. Empty traversals do not call a datasource. Deduplication applies within a stage; this is not a cross-stage or persistent result cache. The interpreter does not promise concurrent execution of different datasource effects.

## Execution and migration

`relation.startingFrom(input)` and `relation.startingFromMany(inputs)` continue to return Kyo effects (`Out < Eff` and `Coll[Out] < Eff`). Datasource callback signatures are unchanged.

The low-level execution model changes: `proof.reify.apply(input)` and `applyMultiple(inputs)` now return a reusable `KyoBatchQuery`. Use the relation’s `startingFrom` / `startingFromMany` to handle Batch, or `toBatch` / `toBatchMany` to leave it to the caller. Low-level plans expose `.toBatch` and compose with `.map` / `.flatMap`. Reusing a query does not reuse fetched results.

Custom `ReifiedRelation` implementations should return `Access[A]` and use `KyoBatchQuery.lift(effect)` for an ordinary Kyo effect, or compose the plans from registered datasource proofs. `lift` is by-name, so its expression is evaluated when the plan runs. Use the datasource constructors when requests should batch by key. `kyoBatch` no longer extends `kyoGeneric`, whose direct-effect interpretation remains unchanged.

## Implementation

A query step is either complete or contains pending requests plus a typed continuation. Zipping pending steps pairs their immutable states. Each request returns an update for its own subtree, allowing the runner to reconstruct results without relying on the order in which Kyo groups sources. Traversal builds a balanced tree so a large input collection does not create a linear-depth zip chain.

The runner advances one stage at a time. Kyo remains responsible for executing datasource effects and batching and deduplicating source requests. No shared mutable builders or result slots are retained in query plans.

## Sharing with native Kyo

Create a `KyoBatchSource[Eff, In, Out](callback)` and pass it to the usual datasource constructor instead of a callback. Its `toBatch(input)` method is a native Kyo computation sharing the same batching identity as the registered relation. Combine independent computations with native `Batch.eval` and handle them together with `Batch.run`. Ordinary sequential composition still introduces dependencies.

Sources group bulk inputs before calling the callback, deduplicate keys, and restore each caller's positions. This adds grouped-key vector/map bookkeeping. A source must return every requested key. Distinct source instances remain independent. This does not import already-created native Batch functions.
