---
lang: en-US
title: Getting Started
---

# Getting Started

The examples use Scala 3 syntax and are compiled with Scala 3.9 by mdoc. When using Scala 2.13, adapt wildcard imports (`*` to `_`) and explicit contextual arguments (`using`) to Scala 2 syntax.

## Installation

The current build targets Scala 2.13 and Scala 3.3 for core, ZQuery, Cats, and generators on JVM and Scala.js. Fetch targets both Scala versions on JVM and Scala 2.13 on Scala.js. Kyo and the ZQuery extension overlay are built with Scala 3.9 on both platforms. Select artifacts for the Scala version supported by your chosen release; module availability is not uniform across all versions.

```sbt
val decrelVersion = "@RELEASEVERSION@"
// Choose the integration(s) you need; core is included transitively.
libraryDependencies += "com.yoohaemin" %% "decrel-zquery" % decrelVersion
libraryDependencies += "com.yoohaemin" %% "decrel-fetch" % decrelVersion
libraryDependencies += "com.yoohaemin" %% "decrel-cats" % decrelVersion
libraryDependencies += "com.yoohaemin" %% "decrel-ziotest" % decrelVersion % Test
libraryDependencies += "com.yoohaemin" %% "decrel-scalacheck" % decrelVersion % Test
// Scala 3.9 modules in the current build:
libraryDependencies += "com.yoohaemin" %% "decrel-zquery-next" % decrelVersion
libraryDependencies += "com.yoohaemin" %% "decrel-kyo" % decrelVersion
libraryDependencies += "com.yoohaemin" %% "decrel-kyo-batch" % decrelVersion
```

Use `decrel-core` directly for a domain module that should have no effect-system dependency. For Scala.js, use sbt's `%%%` cross-platform dependency syntax. Snapshot builds use `@SNAPSHOTVERSION@`.

## 1. Declare the Book Rental domain

Place the relation declarations beside their model's companion. `Self` preserves an input; the other declarations describe exactly one, optional, or many related entities.

```scala mdoc:silent
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
```

## 2. Implement the datasources

ZQuery datasource functions receive a deduplicated `Chunk` of keys and return keyed results. The demo uses `ZIO.attempt` for lookups that can fail, so unknown IDs become failures in the error channel. Optional absence is represented by `None`.

```scala mdoc:silent
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

`Rental.book` and `Rental.user` adapt the primary ID lookups with `contramapOneProof`, reusing those datasource registrations.

## 3. Compose and execute

```scala mdoc:silent
import LibraryDatasources.*
object MainApp extends ZIOAppDefault {
  val userDashboardQuery =
    User.fetch <>: (User.currentRentals >>: (Rental.self & Rental.book))
  def run: ZIO[Any, Throwable, Unit] =
    for {
      result <- userDashboardQuery.startingFrom(User.Id("u1"))
      (user, rentedBooks) = result
      _ <- Console.printLine(s"User: ${user.name}")
      _ <- Console.printLine(s"Rented books: ${rentedBooks.map(_._2.title).mkString(", ")}")
      batch <- userDashboardQuery.startingFrom(List(User.Id("u1"), User.Id("u2")))
      _ <- Console.printLine(s"Fetched ${batch.size} dashboards")
    } yield ()
}
```

Each execution can batch requests at the same dependency stage. A multi-hop graph still has sequential stages; a single `startingFrom` call does not imply one database call.

Continue with [Defining relations](./defining-relations.md) and [Composing relations](./composing-relations.md).
