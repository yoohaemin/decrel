---
lang: en-US
title: Caching & Custom Optimizations
---

# Caching & Custom Optimizations

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

## Pre-populate a cache

`decrel.reify.Cache` stores typed relation/input/output entries. This executable example uses ZQuery. The cache has interpreter adapters, but their operational behavior must be checked per integration; do not infer identical cache injection behavior from a common API.

```scala mdoc:silent
import decrel.reify.Cache
import LibraryDatasources.*
val knownBookId = Book.Id("b1")
val knownBook = books(knownBookId)
val knownRental = rentals.head
val cache = Cache.empty
  .add(Book.fetch, knownBookId, knownBook)
  .add(Book.currentRental, knownBook, Some(knownRental))
val renterQuery = Book.fetch >>: Book.currentRental >>: Rental.user
val renterEffect: ZIO[Any, Throwable, Option[User]] = renterQuery.startingFrom(knownBookId, cache)
```

With these ZQuery cache entries, the first two lookups are satisfied from the supplied cache and only the user lookup needs a datasource. Cache keys include the relation identity and input. A custom/composed relation is a different cache key from its constituent declarations.

### Request-scoped mutable reference

ZQuery offers `Ref[Cache].add`; Fetch exposes the analogous syntax for Cats Effect `Ref[F, Cache]`.

```scala mdoc:silent
val cachedLookup: ZIO[Any, Throwable, Book] = for {
  ref <- Ref.make(Cache.empty)
  _ <- ref.add(Book.fetch, knownBookId, knownBook)
  current <- ref.get
  book <- Book.fetch.startingFrom(knownBookId, current)
} yield book
```

## Specialize a composed relation

`customImpl` wraps an expression so a proof can implement the entire traversal, for example with one SQL join. It keeps the expression's nested result shape: an optimization must still return that shape.

```scala mdoc:silent
val userBooksJoined = (User.currentRentals >>: Rental.book).customImpl
val joinedProof = implementCustomDatasource(userBooksJoined) { (requested: Chunk[User]) =>
  ZIO.attempt(requested.map { user =>
    user -> rentals.filter(_.userId == user.id).map(rental => books(rental.bookId))
  })
}
val joinedResult = userBooksJoined.startingFrom(users(User.Id("u1")))(using joinedProof)
```

This callback is an in-memory stand-in for a batch SQL join. Supply an implicit proof when using the wrapper throughout an application.

## Reify once for repeated use

Implicit resolution happens at compile time. At runtime, materializing proofs and reified relation objects can allocate. Reifying a concrete expression once allows that executable object graph to be reused; it does not create a shared result cache automatically.

```scala mdoc:silent
val dashboard = User.fetch <>: (User.currentRentals >>: Rental.book)
val compiledQuery: ReifiedRelation[User.Id, Throwable, (User, Chunk[Book])] = dashboard.reify
val first = compiledQuery.startingFrom(User.Id("u1"))
val second = compiledQuery.startingFrom(List(User.Id("u1"), User.Id("u2")))
```
