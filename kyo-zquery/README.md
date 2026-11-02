# One Kyo implementation, three execution paths

`decrel-kyo-zquery` adds ZQuery execution to `decrel-kyo-batch`. Register each datasource once using the existing Kyo constructors; the mixin supplies `toZQuery` and `toZQueryMany` using those same proofs.

```scala
import decrel.*
import decrel.reify.{kyoZquery, KyoBatchSource}
import _root_.kyo.{Abort, Async, Sync}

object Lookup extends Relation.Single[Int, String]
object Relations extends kyoZquery[Throwable] {
  val source = KyoBatchSource[Async & Abort[Throwable], Int, String] { ids =>
    Sync.defer(ids.map(id => id -> s"value$id").toMap)
  }
  implicit val lookup: Proof.Single[Lookup.type, Int, String] =
    implementSingleDatasource(Lookup)(source)
}

import Relations.*
val handled = Lookup.startingFrom(1) // String < (Async & Abort[Throwable])
val pending = Lookup.toBatch(1)     // String < (Batch & Async & Abort[Throwable])
val query = Lookup.toZQuery(1)       // ZQuery[Any, Throwable, String]
val native = source.toBatch(1)      // Shares the native batching identity with pending
```

A callback may also be passed directly to `implementSingleDatasource`, `implementOptionalDatasource`, `implementManyDatasource`, or `implementCustomDatasource`. Use an explicit `KyoBatchSource` when native Kyo code should share that source. Callbacks accept immutable `Seq` keys and return keyed `Map` results. Optional results use `Option`; many results use the declared sequence type.

`startingFrom` and `startingFromMany` handle Batch. `toBatch` and `toBatchMany` let the caller combine independent computations under a native `Batch.run`, using `Batch.eval` to expose independent branches. Shared-source grouping deduplicates keys and reconstructs each traversal's order, duplicates, and collection boundaries. Sources from separate registrations remain distinct. Arbitrary pre-existing native Batch functions are not imported.

`toZQuery` and `toZQueryMany` return unexecuted queries. Combine them with batched ZQuery combinators, or return them to Caliban, before execution. The adapter translates source/key requests directly, rather than wrapping an already-executed Kyo plan. Callback effects run through `ZIOs.run`; `Abort[E]` becomes a typed ZQuery failure. The mixin supports `Async & Abort[E]`; handle additional effects inside callbacks.

Reuse the same interpreter/proofs across fields. Fresh query execution fetches again; no persistent result cache is added. Missing response keys cause defects. Callback expressions remain deferred until execution. Shared source descriptors retain native source identity, and each mixin caches ZQuery datasource adapters by descriptor identity.

Native-only applications can use `kyoBatch[Eff]` without this module or ZQuery dependencies. This module builds for Scala 3.9 on JVM and Scala.js, with the Scala 3.3 ZQuery dependency. No Caliban dependency is required.
