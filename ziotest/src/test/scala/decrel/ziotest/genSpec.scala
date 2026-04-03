/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.ziotest

import decrel.*
import decrel.ziotest.gen.*
import zio.test.*
import zio.test.Assertion.*

object genSpec extends ZIOSpecDefault {

  case class RentalFilter(label: String)

  // Relation descriptions
  case class Rental(id: Rental.Id, bookId: Book.Id, userId: User.Id)
  object Rental {
    case class Id(value: String)

    case object self  extends Relation.Self[Rental]
    case object fetch extends Relation.Single[Rental.Id, Rental]
    case object book  extends Relation.Single[Rental, Book]
    case object user  extends Relation.Single[Rental, User]
  }
  case class Book(id: Book.Id, currentRental: Option[Rental.Id])
  object Book {
    case class Id(value: String)

    case object self          extends Relation.Self[Book]
    case object fetch         extends Relation.Single[Book.Id, Book]
    case object currentRental extends Relation.Optional[Book, Rental]
  }
  case class User(id: User.Id, currentRentals: List[Rental.Id])
  object User {
    case class Id(value: String)

    case object self           extends Relation.Self[User]
    case object fetch          extends Relation.Single[User.Id, User]
    case object currentRentals extends Relation.Many[User, List, Rental]
  }

  // Basic Generators
  object gen {
    val rentalId: Gen[Any, Rental.Id] = Gen.uuid.map(uuid => Rental.Id(uuid.toString))
    val bookId: Gen[Any, Book.Id]     = Gen.uuid.map(uuid => Book.Id(uuid.toString))
    val userId: Gen[Any, User.Id]     = Gen.uuid.map(uuid => User.Id(uuid.toString))
  }

  // Relation Implementations
  // Note: not all relations are implemented; we need only implement the ones we use

  implicit val rentalFetch: Proof.Single[
    Rental.fetch.type & Relation.Single[Rental.Id, Rental],
    Rental.Id,
    Rental,
    Nothing
  ] = Gen.relationSingle[Rental.fetch.type, Rental.Id, Rental](Rental.fetch) { (id, _: Option[Nothing]) =>
    (gen.bookId <*> gen.userId).flatMap { case (bookId, userId) =>
      Gen.const(Rental(id, bookId, userId))
    }
  }

  implicit val rentalBook: Proof.Single[
    Rental.book.type & Relation.Single[Rental, Book],
    Rental,
    Book,
    Nothing
  ] = Gen.relationSingle[Rental.book.type, Rental, Book](Rental.book) { (rental, _: Option[Nothing]) =>
    Gen.const(Book(rental.bookId, Some(rental.id)))
  }

  implicit val rentalUser: Proof.Single[
    Rental.user.type & Relation.Single[Rental, User],
    Rental,
    User,
    Nothing
  ] = Gen.relationSingle[Rental.user.type, Rental, User](Rental.user) { (rental, _: Option[Nothing]) =>
    Gen.const(User(rental.userId, List(rental.id)))
  }

  implicit val userCurrentRentals: Proof.Many[
    User.currentRentals.type & Relation.Many[User, List, Rental],
    User,
    List,
    Rental,
    Nothing
  ] = Gen.relationMany[User.currentRentals.type, User, Rental, List](User.currentRentals) { (user, _: Option[Nothing]) =>
    Gen
      // Use `expand` even when implementing other relations
      .listOf(gen.rentalId.expand(Rental.fetch))
      // Make it consistent
      .map(_.map(_.copy(userId = user.id)))
  }

  override def spec: Spec[Environment, Any] =
    suite("proof - zio.test.Gen")(
      test("Simple relation") {
        val relation = Rental.fetch

        val staticRentalId = Rental.Id("foo")
        val rentalIdGen    = Gen.const(staticRentalId)

        check(rentalIdGen.expand(relation)) { (rental: Rental) =>
          assert(rental.id)(equalTo(staticRentalId))
        }
      },
      test("relation builders receive the filter") {
        var sawFilter = false

        implicit val localRentalFetch: Proof.Single[
          Rental.fetch.type & Relation.Single[Rental.Id, Rental],
          Rental.Id,
          Rental,
          RentalFilter
        ] = Gen.relationSingle[Rental.fetch.type, Rental.Id, Rental, RentalFilter](Rental.fetch) { (id, filter: Option[RentalFilter]) =>
          sawFilter = filter.contains(RentalFilter("exact"))
          Gen.const(Some(Rental(id, Book.Id("book"), User.Id("user"))))
        }

        val staticRentalId = Rental.Id("foo")

        check(Gen.const(staticRentalId).expand(Rental.fetch.filter(RentalFilter("exact")))) { (rental: Option[Rental]) =>
          assertTrue(
            sawFilter,
            rental.contains(Rental(staticRentalId, Book.Id("book"), User.Id("user")))
          )
        }
      },
      test("Composing with &") {
        val relation = Rental.fetch & Rental.fetch

        check(gen.rentalId.expand(relation)) { case (rental1: Rental, rental2: Rental) =>
          assert(rental1.id)(equalTo(rental2.id))
          assert(rental1)(not(equalTo(rental2)))
        }
      },
      test("Composing with >>:") {
        val relation = Rental.fetch >>: Rental.book

        val staticRentalId = Rental.Id("foo")
        val rentalIdGen    = Gen.const(staticRentalId)

        check(rentalIdGen.expand(relation)) { (book: Book) =>
          assert(book.currentRental)(isSome(equalTo(staticRentalId)))
        }
      },
      test("Composing with <>:") {
        val relation = Rental.fetch <>: Rental.book

        val staticRentalId = Rental.Id("foo")
        val rentalIdGen    = Gen.const(staticRentalId)

        check(rentalIdGen.expand(relation)) { case (rental, book) =>
          assert(rental.id)(equalTo(staticRentalId))
          assert(book.currentRental)(isSome(equalTo(staticRentalId)))
        }
      },
      test("Complex relations") {
        val relation = Rental.fetch >>: Rental.user >>: (User.self & User.currentRentals)

        check(gen.rentalId.expand(relation)) { case (user, rentals) =>
          assert(rentals.map(_.userId))(forall(equalTo(user.id)))
        }
      }
    )
}
