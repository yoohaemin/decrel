---
lang: en-US
title: Typed filters
---

# Typed filters

A filter is a description of a condition on one relation's input and one candidate
output. The implementing module can inspect it and translate it into its own query
representation. Decrel carries the description through reification and composition.

## Define fields with ZIO Blocks optics

Add `"com.yoohaemin" %% "decrel-filter-schema" % "@RELEASEVERSION@"` alongside your
Decrel integration. The adapter uses ZIO Blocks Schema 0.0.51 and supports Scala 2.13
and Scala 3 on JVM and Scala.js. Core and the backend interfaces do not depend on
ZIO Blocks.

```scala mdoc
import decrel._
import decrel.filter._
import decrel.filter.schema.syntax._
import zio.blocks.schema.{CompanionOptics, Lens, Schema}

case class Customer(budget: BigDecimal)
object Customer extends CompanionOptics[Customer] {
  implicit val schema: Schema[Customer] = Schema.derived
  val budget: Lens[Customer, BigDecimal] = optic(_.budget)
  case object books extends Relation.Many[Customer, List, Book]
}

case class Book(price: BigDecimal, discount: Option[Int])
object Book extends CompanionOptics[Book] {
  implicit val schema: Schema[Book] = Schema.derived
  val price: Lens[Book, BigDecimal] = optic(_.price)
  val discount: Lens[Book, Option[Int]] = optic(_.discount)
  case object publisher extends Relation.Single[Book, String]
}

val affordableBooks = Customer.books.filter { (in, out) =>
  (out(Book.price) <= in(Customer.budget)) &&
  out(Book.discount).exists(_ > 0)
}

val publishers = affordableBooks >>: Book.publisher
```

`in` represents a `Customer` and `out` represents one `Book`, even though the edge
returns `List[Book]`. The callback runs once when constructing the expression. Its
result contains field paths and literals, not the callback or the optics' getters.

A predicate can also be constructed independently and then attached:

```scala mdoc
val withinBudget = Predicate.build[Customer, Book] { (in, out) =>
  out(Book.price) <= in(Customer.budget)
}
val selectedBooks = Customer.books.filter(withinBudget)
val description: Either[FilterError, Predicate.Test] = withinBudget.toAst
```

Only `Single`, `Optional`, and `Many` edges can be filtered. Combine conditions with
`&&` or `||` inside one filter. Filtering composed expressions, `customImpl` wrappers,
or already-filtered expressions is not supported.

## Operators and missing values

The initial language supports `===`, `!==`, `<`, `<=`, `>`, `>=`, `&&`, `||`, `!`,
`Predicate.always`, and `Predicate.never`. Values can be literals or typed field
references, including nested record fields. Equality supports Boolean, Byte, Short,
Int, Long, Float, Double, Char, String, BigInt, and BigDecimal. Ordering supports the
same types except Boolean. Both sides must have the same type; the language does
not insert numeric conversions or accept custom `Ordering` instances.

Numeric comparisons follow the corresponding Scala operators, including IEEE
floating-point comparisons. Strings and characters use case-sensitive lexicographic
code-unit ordering. A backend must preserve these semantics for its supported
domains or report an unsupported operation.

An `Option[A]` field exposes `isDefined`, `isEmpty`, and `exists`. The body of
`exists` can compare its payload, select fields of an optional record, or reference
the surrounding edge input/output. It is false for `None`. Boolean operators use
ordinary two-valued logic: `!field.exists(p)` is true for an absent field. A SQL
compiler must account for SQL's different null behavior rather than directly
substituting nullable comparisons under `NOT`.

Option payload references are scoped to their `exists` body. A reference captured
and reused outside that body is rejected when the predicate is attached, built,
or passed to a `FilterCompiler`. Invalid construction raises
`IllegalArgumentException`; `toAst` and compilation return `Left(FilterError)`.

The adapter accepts total record-field lenses. Traversals and prisms cannot be used
as scalar fields; non-field optic paths are rejected by `syntax.fromLens`, which
returns an `Either`, or by the implicit lens conversion, which raises
`IllegalArgumentException`. Arithmetic, string functions, collection quantifiers,
membership, custom scalar wrappers, and custom expression nodes are deferred.

## Implementing a filtered relation

Filter-aware data-source builders are named `implementFilteredSingleDatasource`,
`implementFilteredOptionalDatasource`, and `implementFilteredManyDatasource`.
Their callback receives the integration's batch of original inputs and
`Option[Predicate[In, Row]]`. `None` means unfiltered access.

For example, a ZQuery module can accept a backend callback with this contract:

```scala mdoc
import zio.{Chunk, ZIO}

object BookRelations extends decrel.reify.zquery[Any] {
  def implement(
    run: (Chunk[Customer], Option[Predicate[Customer, Book]]) =>
      ZIO[Any, String, Chunk[(Customer, List[Book])]]
  ): Proof.Many[
    Customer.books.type with Relation.Many[Customer, List, Book],
    Customer, String, List, Book, Predicate[Customer, Book]
  ] = implementFilteredManyDatasource[
    Customer.books.type, Customer, String, List, Book
  ](Customer.books)(run)
}
```

The backend owns field-to-storage mappings, compilation, and execution. Each input
must receive its own results: inputs sharing an ID can still differ in fields used
by the predicate. Return `None` or an empty collection for rejected candidates;
do not omit required input entries from the batch response.

| Edge | Filtered result | Filter-aware callback result for each input |
| --- | --- | --- |
| `Single[In, Row]` | `Option[Row]` | `Option[Row]` |
| `Optional[In, Row]` | `Option[Row]` | `Option[Row]` |
| `Many[In, CC, Row]` | `CC[Row]` | `CC[Row]` |

A filter-aware single implementation must return `Some` during unfiltered access;
otherwise Decrel raises `IllegalStateException`. Many implementations preserve the
order of retained candidates. Existing composition preserves nested `Option` and
collection shapes and short-circuits an absent optional result.

Implement `FilterCompiler[Plan]` to translate the public, closed `Predicate.Test`
tree into a backend plan. Its final `compile` method validates scopes before calling
the protected `compileAst`. Unsupported nodes, paths, or scalar semantics must
return `Left(FilterError(path, reason))`; the implementing module maps that error
into its effect. Never ignore an unsupported condition or silently fetch unfiltered
results. No SQL compiler or in-memory evaluator is supplied in this version.

Predicates and filtered relation keys use structural equality. Independently built
equal trees on the same base relation can share requests and cached sources. Input
and output roots, literal types/values, and different predicates remain distinct.
This is structural equality, not algebraic equivalence: rearranging an `AND` does
not canonicalize the predicate.

## Migrating branch implementations

- Replace arbitrary filter case classes with `Predicate[In, Row]` and the typed DSL.
- Use the named filter-aware builders above; their type arguments no longer include
  an arbitrary filter type. The proof's final type argument is `Predicate[In, Row]`.
- Keep `implementSingleDatasource`, `implementOptionalDatasource`, and
  `implementManyDatasource` for unfiltered implementations. Their proof filter type
  remains `Nothing`. `implementCustomDatasource` is unfiltered only.
- ScalaCheck and ZIO Test use `Gen.filteredRelationSingle`,
  `Gen.filteredRelationOptional`, and `Gen.filteredRelationMany` with the same
  predicate and cardinality contracts, on one input at a time.
- Function-based `contramap`, `contramapOptional`, `contramapMany`, and integration
  `contramap*Proof` helpers now produce unfiltered proofs. Implement a derived edge
  directly when its predicates need the original input. Decrel does not rewrite
  predicates through arbitrary Scala functions.

Filters see earlier records only if those records are already part of the edge's
input. They cannot reference sibling joins or filter the entire result graph.
Composition retains Decrel's traversal semantics; this release does not add flat
SQL rows, query optimization, aggregation, sorting, pagination, or serialization.
