---
lang: en-US
title: Defining Relations
---

# Defining Relations

`Relation[-In, +Out]` describes an edge or traversal. The three declared cardinalities and the built-in identity are:

| Declaration | Output | Meaning |
| --- | --- | --- |
| `Single[In, Out]` | `Out` | Exactly one value, if interpretation succeeds |
| `Optional[In, Out]` | `Option[Out]` | Zero or one value |
| `Many[In, CC, Out]` | `CC[Out]` | A collection of values |
| `Self[A]` | `A` | The unchanged input, without a datasource |

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

```scala mdoc:silent
val single: Relation[Book.Id, Book] = Book.fetch
val optional: Relation[Book, Option[Rental]] = Book.currentRental
val many: Relation[User, Chunk[Rental]] = User.currentRentals
val withInput = Relation.Self[Rental] & Rental.book & Rental.user
val withInputShape: Relation[Rental, (Rental, Book, User)] = withInput
```

Use finite, ordered collections such as `List`, `Vector`, and supported `Chunk` versions. Bulk composition relies on preserving duplicates, positions, and group boundaries. Convert arrays or sets explicitly rather than assuming arbitrary `Iterable` instances satisfy those laws.

## Reuse proofs through foreign keys

An entity relation should usually adapt an existing ID lookup. In the library example, `Rental.book` forwards `rental.bookId` to the `Book.fetch` proof. This lets the interpreter reuse the same underlying datasource registration.

Inside a ZQuery module, the helpers support one, optional, and many foreign keys:

```scala mdoc:silent
object AdaptedLibrary extends zquery[Any] {
  case class ReadingList(primary: Book.Id, optional: Option[Book.Id], favorites: List[Book.Id])
  object ReadingList {
    case object primary extends Relation.Single[ReadingList, Book]
    case object optional extends Relation.Optional[ReadingList, Book]
    case object favorites extends Relation.Many[ReadingList, List, Book]
  }
  val bookFetch: Proof.Single[Book.fetch.type, Book.Id, Throwable, Book] =
    implementSingleDatasource(Book.fetch)(ids => ZIO.attempt(ids.map(id => id -> LibraryDatasources.books(id))))
  val primaryProof = contramapOneProof(bookFetch, ReadingList.primary, (r: ReadingList) => r.primary)
  val optionalProof = contramapOptionalProof(bookFetch, ReadingList.optional, (r: ReadingList) => r.optional)
  val favoritesProof = contramapManyProof(bookFetch, ReadingList.favorites, (r: ReadingList) => r.favorites)
  val methodProof = bookFetch.contramap(ReadingList.primary)((r: ReadingList) => r.primary)
}
```

The datasource helpers are available in ZQuery, Fetch, and Kyo Batch. Proof-instance methods provide adaptation for generic interpreters too. Their available collection/optional transformations depend on the proof kind; do not assume every combination of nested containers is flattened automatically.
