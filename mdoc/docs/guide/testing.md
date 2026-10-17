---
lang: en-US
title: Property-Based Testing
---

# Property-Based Testing with Relations

Generating a user and their rentals independently can violate foreign-key constraints. Generator proofs let you encode those constraints once and reuse a relation expression through `Gen.expand`.

Consistency is local to the implementations you supply. These interpreters do not maintain a shared generated database, and deeper traversals can forget earlier contextual constraints. In particular, the base implementation of `Single <>:` duplicates its left evaluation; see [PR #392](https://github.com/yoohaemin/decrel/pull/392). The examples below zip `Self` with an existing input and use `Many <>:`.

## ZIO Test

```scala mdoc:silent
import decrel.*
case class User(id: String)
object User {
  case object self extends Relation.Self[User]
  case object rentals extends Relation.Many[User, List, Rental]
}
case class Rental(id: String, bookId: String, userId: String)
object Rental {
  case object self extends Relation.Self[Rental]
  case object book extends Relation.Single[Rental, Book]
}
case class Book(id: String, rentalId: String)
```

```scala mdoc:silent
import decrel.ziotest.gen.*
import zio.test.*
object LibraryGenSpec extends ZIOSpecDefault {
  val userGen: Gen[Any, User] = Gen.uuid.map(id => User(id.toString))
  implicit val rentalsProof: Proof.Many[User.rentals.type, User, List, Rental] =
    Gen.relationMany(User.rentals) { user =>
      Gen.listOf(Gen.uuid).map(ids => ids.map(id => Rental(id.toString, "book-" + id, user.id)))
    }
  implicit val bookProof: Proof.Single[Rental.book.type, Rental, Book] =
    Gen.relationSingle(Rental.book)(rental => Gen.const(Book(rental.bookId, rental.id)))
  val query = User.self & (User.rentals <>: Rental.book)
  val data: Gen[Any, (User, List[(Rental, Book)])] = userGen.expand(query)
  def spec: Spec[Environment, Any] = suite("Relational generators")(
    test("rentals preserve user keys and books preserve rental keys") {
      check(data) { case (user, pairs) =>
        assertTrue(pairs.forall { case (rental, book) =>
          rental.userId == user.id && book.id == rental.bookId && book.rentalId == rental.id
        })
      }
    }
  )
}
```

Import `decrel.ziotest.gen.randomWithSized.*` instead when your generator environment is `Random & Sized`. Constructors include `Gen.relationSingle`, `Gen.relationOptional`, and `Gen.relationMany`; `.expand` is defined on an input generator.

## ScalaCheck

This is a separate, complete example with the same model and constraints. ScalaCheck needs its own proofs; ZIO Test proofs are not interchangeable.

```scala mdoc:reset:silent
import decrel.*
case class User(id: String)
object User {
  case object self extends Relation.Self[User]
  case object rentals extends Relation.Many[User, List, Rental]
}
case class Rental(id: String, bookId: String, userId: String)
object Rental {
  case object self extends Relation.Self[Rental]
  case object book extends Relation.Single[Rental, Book]
}
case class Book(id: String, rentalId: String)
```

```scala mdoc:silent
import decrel.scalacheck.gen.*
import org.scalacheck.{Gen, Properties}
import org.scalacheck.Prop.forAll
object ScalaCheckRelationSpec extends Properties("Relations") {
  implicit val rentalsProof: Proof.Many[User.rentals.type, User, List, Rental] =
    Gen.relationMany(User.rentals) { user =>
      Gen.listOf(Gen.uuid).map(ids => ids.map(id => Rental(id.toString, "book-" + id, user.id)))
    }
  implicit val bookProof: Proof.Single[Rental.book.type, Rental, Book] =
    Gen.relationSingle(Rental.book)(rental => Gen.const(Book(rental.bookId, rental.id)))
  val query = User.self & (User.rentals <>: Rental.book)
  val data = Gen.uuid.map(id => User(id.toString)).expand(query)
  property("foreign keys agree") = forAll(data) { case (user, pairs) =>
    pairs.forall { case (rental, book) =>
      rental.userId == user.id && book.id == rental.bookId && book.rentalId == rental.id
    }
  }
}
```

Use domain-specific generators for meaningful test coverage. A relation declaration alone cannot infer how your foreign keys should be populated.
