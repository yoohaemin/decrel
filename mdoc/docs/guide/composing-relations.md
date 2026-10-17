---
lang: en-US
title: Composing Relations
---

# Composing Relations

Import `decrel.*` for the operators. The left side of sequential composition must be a declared `Single`, `Optional`, or `Many`; retain the inferred concrete AST type of composed values so implicit proofs can be derived.

| Operator | Purpose | Single-left output |
| --- | --- | --- |
| `a >>: b` | Follow `a`, then `b`; discard the intermediate value | `C` |
| `a <>: b` | Follow `a`, then `b`; retain the intermediate value | `(B, C)` |
| `a & b` / `a.zip(b)` | Combine branches from the same input | `(B, C)` |

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

## Sequential composition

```scala mdoc:silent
val rentalBook = Rental.fetch >>: Rental.book
val currentRenter = Book.currentRental >>: Rental.user
val rentedBooks = User.currentRentals >>: Rental.book
val oneShape: Relation[Rental.Id, Book] = rentalBook
val optionalShape: Relation[Book, Option[User]] = currentRenter
val manyShape: Relation[User, Chunk[Book]] = rentedBooks
```

An absent optional input skips its downstream branch. A many-valued left side traverses its elements and preserves the supported collection shape. Nested many-valued relations can produce nested collections; sequential composition does not flatten every level.

## Zip independent branches

```scala mdoc:silent
val rentalView = Rental.self & Rental.book & Rental.user
val viewShape: Relation[Rental, (Rental, Book, User)] = rentalView
val bookAndUser = Rental.book.zip(Rental.user)
```

`Zippable` combines compatible tuple results into flat tuples, with instances through arity 22. Actual batching or parallelism is determined by the interpreter; a generic Monad implementation can evaluate the branches sequentially.

## Retain the intermediate node

```scala mdoc:silent
val retainedRental = Rental.fetch <>: Rental.book
val retainedOptional = Book.currentRental <>: Rental.user
val retainedMany = User.currentRentals <>: Rental.book
val optionalPair: Relation[Book, Option[(Rental, User)]] = retainedOptional
val manyPairs: Relation[User, Chunk[(Rental, Book)]] = retainedMany
```

The retained value is paired inside `Option` or inside each collection element. This differs from independently fetching two collections and zipping their results afterward.

The existing `Single <>:` implementation on the base branch duplicates its left AST. The correctness fix in [PR #392](https://github.com/yoohaemin/decrel/pull/392) changes it to evaluate the left side once. Until that fix is included, use caution with nonmemoized effects and nondeterministic generators; ZQuery request caching can mask the duplication.

## Precedence and associativity

`>>:` and `<>:` have equal precedence and associate right because their names end in `:`. Their leading characters give them higher precedence than `&`. Parenthesize a right-hand zip explicitly:

```scala mdoc:silent
val implicitGrouping = User.fetch <>: User.currentRentals >>: Rental.book
val explicitGrouping = User.fetch <>: (User.currentRentals >>: Rental.book)
val branched = Rental.fetch >>: (Rental.book & Rental.user)
assert(implicitGrouping == explicitGrouping)
```

For a multi-step named value, compose the downstream expression on the right; widening it to `Relation[In, Out]` loses the structure used by proof derivation.
