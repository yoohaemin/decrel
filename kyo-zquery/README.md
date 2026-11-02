# Kyo callbacks with ZQuery batching

`decrel-kyo-zquery` lets you implement datasource batch callbacks in Kyo while ZQuery organizes requests. It depends on `decrel-zquery` and Kyo's `kyo-zio` bridge, independently of `decrel-kyo-batch`.

Add `"com.yoohaemin" %% "decrel-kyo-zquery" % "<version>"` to your dependencies.

```scala
import decrel.*
import decrel.reify.kyoZquery
import _root_.kyo.Sync

object Lookup extends Relation.Single[Int, String]
object Interpreter extends kyoZquery {
  implicit val lookup: Proof.Single[Lookup.type, Int, String, String] =
    implementSingleKyoDatasource(Lookup) { ids =>
      Sync.defer(ids.map(id => id -> s"value$id"))
    }
}

import Interpreter.*
val first = Lookup.toQueryMany(Vector(1, 2, 1))
val second = Lookup.toQuery(2)
val combined = first.zipWithBatched(second)((_, _))
// Execute combined.run at the outer ZIO boundary, or return the queries to Caliban.
```

The Kyo constructors are `implementSingleKyoDatasource`, `implementOptionalKyoDatasource`, `implementManyKyoDatasource`, and `implementCustomKyoDatasource`. They accept ZIO `Chunk` inputs and return a `Chunk` of keyed results in `Abort[E] & Async`. `Sync` and pure computations are also accepted. Handle additional effects such as `Env` inside your callback. Callback expressions are evaluated only when ZQuery executes a batch, including on each fresh execution.

Callbacks must return one keyed result for every requested key. Optional callbacks return `Option[Out]`; many callbacks return the declared immutable sequence type. Result order inside the callback is irrelevant. ZQuery restores input positions, duplicates, and nested collection boundaries. Missing keys follow the existing ZQuery interpreter contract and cause a defect.

All existing ZQuery operations remain available, including `toQuery`, `toQueryMany`, and the ZIO execution helpers. Return unexecuted queries when a caller such as Caliban should combine requests across fields. Share the same interpreter/proof instances across those fields. Calling `.run` separately creates separate batching scopes.

This module does not interpret native Kyo `Batch` computations. It bridges datasource callback effects; ZQuery handles batching, deduplication, dependency stages, and query caching. Kyo `Abort[E]` failures become typed ZQuery failures through `ZIOs.run`; interruption and defects use that bridge's semantics.

The module is built for Scala 3.9 on JVM and Scala.js, using the Scala 3.3 ZQuery module.
