---
lang: en-US
home: true
heroText: decrel
tagline: Declarative data relations for Scala — batched fetching and reusable test generators.
actions:
- text: Get Started →
  link: /guide/getting-started.html
  type: primary
features:
- title: First-Class Domain Relations
  details: Turn implicit relationships into typed Scala values alongside your domain models.
- title: Batching Through Interpreters
  details: ZQuery batches independent requests and deduplicates repeated keys when backed by batch datasources.
- title: Declare Once, Interpret Anywhere
  details: Interpret a relation graph with ZQuery, Fetch, generators, or a custom effect. Kyo support is experimental.
- title: Reusable Test Generators
  details: Encode local relational constraints in generator proofs and reuse your expressions with expand.
- title: Composable Graph Algebra
  details: Follow relations, retain intermediate values, and zip independent branches with typed result shapes.
- title: Lightweight Domain Core
  details: decrel-core depends on izumi-reflect and the Scala standard library, without requiring an effect system.
footer: MPL-2.0 Licensed | Copyright © 2022-2026 Haemin Yoo
---

## Express the graph you want to traverse

Domain relationships often exist only in SQL foreign keys or application code. Decrel represents those relationships as values and delegates execution to an interpreter.


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

Keep the inferred concrete type of a composed relation: its AST type is needed to derive a `Proof`. A broad `Relation[In, Out]` annotation is useful for checking the result shape, but should not replace the executable value's inferred type.

```scala mdoc:silent
val userWithRentedBooks = User.fetch <>: (User.currentRentals >>: Rental.book)
val shape: Relation[User.Id, (User, Chunk[Book])] = userWithRentedBooks
```

With the complete datasource module from [Getting started](./guide/getting-started.md), execute that same expression for one or several IDs:

```scala mdoc:invisible
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
import LibraryDatasources.*
val result: ZIO[Any, Throwable, List[(User, Chunk[Book])]] =
  userWithRentedBooks.startingFrom(List(User.Id("u1"), User.Id("u2")))
```

The [testing guide](./guide/testing.md) shows how to provide generator proofs and call `inputGen.expand(relation)`. Generator proofs establish the constraints you encode; they do not create a globally consistent graph automatically.

Read [composition](./guide/composing-relations.md), [integrations](./guide/integrations.md), or the [e-commerce showcase](./showcase/README.md) next.
