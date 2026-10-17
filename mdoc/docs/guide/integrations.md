---
lang: en-US
title: Ecosystem Integrations
---

# Ecosystem Integrations

ZQuery is the primary integration and has the broadest batching/caching test coverage. Fetch is secondary. Kyo is experimental. Generic effects and generators have their own execution semantics.

The examples on this page use the domain and `LibraryDatasources` from [Getting started](./getting-started.md). Each page is compilation-checked with that setup.

```scala mdoc:invisible
import decrel.*
import zio.Chunk

case class User(id: User.Id, name: String, currentRentalIds: List[Rental.Id] = Nil)
object User {
  case class Id(value: String)
  case object self extends Relation.Self[User]
  case object fetch extends Relation.Single[User.Id, User]
  case object currentRentals extends Relation.Many[User, Chunk, Rental]
}
case class Book(id: Book.Id, title: String, currentRentalId: Option[Rental.Id] = None)
object Book {
  case class Id(value: String)
  case object self extends Relation.Self[Book]
  case object fetch extends Relation.Single[Book.Id, Book]
  case object currentRental extends Relation.Optional[Book, Rental]
}
case class Rental(id: Rental.Id, bookId: Book.Id, userId: User.Id)
object Rental {
  case class Id(value: String)
  case object self extends Relation.Self[Rental]
  case object fetch extends Relation.Single[Rental.Id, Rental]
  case object book extends Relation.Single[Rental, Book]
  case object user extends Relation.Single[Rental, User]
}
import decrel.reify.zquery
import zio.*

object LibraryDatasources extends zquery[Any] {
  val users = Map(
    User.Id("u1") -> User(User.Id("u1"), "Alice", List(Rental.Id("r1"), Rental.Id("r2"))),
    User.Id("u2") -> User(User.Id("u2"), "Bob")
  )
  val books = Map(
    Book.Id("b1") -> Book(Book.Id("b1"), "Programming in Scala", Some(Rental.Id("r1"))),
    Book.Id("b2") -> Book(Book.Id("b2"), "Zionomicon", Some(Rental.Id("r2"))),
    Book.Id("b3") -> Book(Book.Id("b3"), "Scala with Cats")
  )
  val rentals = Chunk(
    Rental(Rental.Id("r1"), Book.Id("b1"), User.Id("u1")),
    Rental(Rental.Id("r2"), Book.Id("b2"), User.Id("u1"))
  )
  implicit val userFetch: Proof.Single[User.fetch.type, User.Id, Throwable, User] =
    implementSingleDatasource(User.fetch)(ids => ZIO.attempt(ids.map(id => id -> users(id))))
  implicit val bookFetch: Proof.Single[Book.fetch.type, Book.Id, Throwable, Book] =
    implementSingleDatasource(Book.fetch)(ids => ZIO.attempt(ids.map(id => id -> books(id))))
  implicit val rentalFetch: Proof.Single[Rental.fetch.type, Rental.Id, Throwable, Rental] =
    implementSingleDatasource(Rental.fetch) { ids =>
      ZIO.attempt(ids.map(id => id -> rentals.find(_.id == id).get))
    }
  implicit val userCurrentRentals: Proof.Many[User.currentRentals.type, User, Nothing, Chunk, Rental] =
    implementManyDatasource(User.currentRentals) { requested =>
      ZIO.succeed(requested.map(user => user -> rentals.filter(_.userId == user.id)))
    }
  implicit val bookCurrentRental: Proof.Optional[Book.currentRental.type, Book, Nothing, Rental] =
    implementOptionalDatasource(Book.currentRental) { requested =>
      ZIO.succeed(requested.map(book => book -> rentals.find(_.bookId == book.id)))
    }
  implicit val rentalBook: Proof.Single[Rental.book.type, Rental, Throwable, Book] =
    contramapOneProof(bookFetch, Rental.book, (rental: Rental) => rental.bookId)
  implicit val rentalUser: Proof.Single[Rental.user.type, Rental, Throwable, User] =
    contramapOneProof(userFetch, Rental.user, (rental: Rental) => rental.userId)
}
```

## ZIO and ZQuery

Extend `decrel.reify.zquery[R]`. Its datasource constructors take keyed batch functions:

| Constructor | Batch function result |
| --- | --- |
| `implementSingleDatasource` | `ZIO[R, E, Chunk[(In, Out)]]` |
| `implementOptionalDatasource` | `ZIO[R, E, Chunk[(In, Option[Out])]]` |
| `implementManyDatasource` | `ZIO[R, E, Chunk[(In, CC[Out])]]` |

The input is a deduplicated `Chunk[In]`. Return an entry for each requested key; ordering of keyed results is immaterial. A missing result currently causes a defect when the result map is indexed. Fail the ZIO error channel deliberately for domain failures rather than omitting requested keys.

```scala mdoc:silent
val zqueryExamples = {
  import LibraryDatasources.*
  val single: ZIO[Any, Throwable, Book] = Book.fetch.startingFrom(Book.Id("b1"))
  val bulk: ZIO[Any, Throwable, Vector[Book]] = Book.fetch.startingFrom(Vector(Book.Id("b1")))
  val query: zio.query.ZQuery[Any, Throwable, Book] = Book.fetch.startingFromQuery(Book.Id("b1"))
  (single, bulk, query)
}
```

`startingFrom` aliases `toZIO` / `toZIOMany`; `startingFromQuery` aliases `toQuery` / `toQueryMany`. Compose `ZQuery` values before `.run` when their requests should share one execution.

`CommonError` derives a common error type for composed proofs: an infallible child preserves the other child's error type; related errors can widen to a shared supertype. The original error values are preserved.

### Input-side extension syntax

The current `decrel-zquery-next` module is compiled with Scala 3.9. Add the matching artifact and mix `zqueryNextSyntax[R]` into the interpreter instance:

```scala mdoc:silent
import decrel.reify.zqueryNextSyntax
object NextLibrary extends zquery[Any] with zqueryNextSyntax[Any] {
  implicit val bookProof: Proof.Single[Book.fetch.type, Book.Id, Throwable, Book] =
    implementSingleDatasource(Book.fetch)(ids => ZIO.attempt(ids.map(id => id -> LibraryDatasources.books(id))))
}
val expanded = {
  import NextLibrary.*
  val effect: ZIO[Any, Throwable, Book] = Book.Id("b1").expand(Book.fetch)
  val query: zio.query.ZQuery[Any, Throwable, Book] = Book.Id("b1").expandQuery(Book.fetch)
  (effect, query)
}
```

## Cats Effect and Fetch

Use an interpreter instance with `Concurrent[F]`. A constructor parameter avoids reading an uninitialized abstract `val` during trait construction on versions before the initialization fix in [PR #395](https://github.com/yoohaemin/decrel/pull/395).

```scala mdoc:silent
import _root_.cats.effect.{Concurrent, IO}
class FetchLibrary(protected implicit val CF: Concurrent[IO]) extends decrel.reify.fetch[IO] {
  implicit val bookProof: Proof.Single[Book.fetch.type, Book.Id, Book] =
    implementSingleDatasource(Book.fetch) { ids =>
      IO(ids.map(id => id -> LibraryDatasources.books(id)))
    }
}
val fetchLibrary = new FetchLibrary
val fetchExamples = {
  import fetchLibrary.*
  val effect: IO[Book] = Book.fetch.startingFrom(Book.Id("b1"))
  val query: _root_.fetch.Fetch[IO, Book] = Book.fetch.startingFromFetch(Book.Id("b1"))
  (effect, query)
}
```

Fetch constructors receive `List[In]` and return `F[List[(In, Out)]]` (with `Option` or a collection in the corresponding result). `startingFrom` aliases `toF` / `toFMany`; `startingFromFetch` aliases `toFetch` / `toFetchMany`. Running requires `Clock[F]`, provided for IO.

Return exactly the requested identifiers, with no duplicates. Batch validation rejects missing, duplicate, and unrequested keys. Singleton validation is hardened by [PR #398](https://github.com/yoohaemin/decrel/pull/398). Stable per-registration batching/cache identity is corrected in [PR #399](https://github.com/yoohaemin/decrel/pull/399); those fixes are separate from this documentation branch.

## Kyo

`decrel-kyo-batch` interprets relations through `kyo.Batch`. The batch callback takes a `Seq[In]` and returns `Map[In, Out] < Eff`.

```scala mdoc:silent
import _root_.kyo.*
object KyoLibrary extends decrel.reify.kyoBatch[Any] {
  implicit val bookProof: Proof.Single[Book.fetch.type, Book.Id, Book] =
    implementSingleDatasource(Book.fetch) { ids =>
      ids.map(id => id -> Book(id, "Example book")).toMap
    }
}
val kyoExamples = {
  import KyoLibrary.*
  val single: Book < Any = Book.fetch.startingFrom(Book.Id("b1"))
  val bulk: List[Book] < Any = Book.fetch.startingFromMany(List(Book.Id("b1")))
  (single, bulk)
}
```

This in-memory example uses `Any` as its effect requirement and only needs the transitive Kyo Prelude dependency. For asynchronous access, add the matching Kyo runtime dependency and choose the effect required by your datasource.

The Kyo cache adapter remains unfinished. Treat this integration as experimental rather than assuming parity with ZQuery.

## Custom datatypes

- Extend `decrel.reify.catsMonad[F]` and supply its protected `Monad[F]` member for a Cats Monad.
- Import `decrel.reify.either.*` for the existing `Either` interpreter.
- Extend `monofunctor.module[F]` for a one-parameter effect, implementing `flatMap`, `map`, and `succeed`.
- Extend `bifunctor.module[F]` for a covariant two-parameter effect.

Override traversal for an effect's batching semantics. The typed-zip extension point in [PR #396](https://github.com/yoohaemin/decrel/pull/396) also requires a batching-aware `zipWith` override when applicable.
