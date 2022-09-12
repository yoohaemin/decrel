/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.scalacheck

import decrel.*
import decrel.scalacheck.gen.*
import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop.forAll

object genSpec extends Properties("Relations") {

  // Relation descriptions
  case class Rental(id: Rental.Id, bookId: Book.Id, userId: User.Id)

  object Rental {
    case class Id(value: String)

    case object fetch extends Relation.Single[Rental.Id, Rental]
    case object self  extends Relation.Self[Rental]
    case object book  extends Relation.Single[Rental, Book]
    case object user  extends Relation.Single[Rental, User]
  }

  case class Book(id: Book.Id, currentRental: Option[Rental.Id])

  object Book {
    case class Id(value: String)

    case object fetch         extends Relation.Single[Book.Id, Book]
    case object self          extends Relation.Self[Book]
    case object currentRental extends Relation.Optional[Book, Rental]
  }

  case class User(id: User.Id, currentRentals: List[Rental.Id])

  object User {
    case class Id(value: String)

    case object fetch          extends Relation.Single[User.Id, User]
    case object self           extends Relation.Self[User]
    case object currentRentals extends Relation.Many[User, List, Rental]
  }

  // Basic Generators
  object gen {
    val rentalId: Gen[Rental.Id] = Gen.uuid.map(uuid => Rental.Id(uuid.toString))
    val bookId: Gen[Book.Id]     = Gen.uuid.map(uuid => Book.Id(uuid.toString))
    val userId: Gen[User.Id]     = Gen.uuid.map(uuid => User.Id(uuid.toString))
  }

  // Relation Implementations
  // Note: not all relations are implemented; we need only implement the ones we use

  implicit val rentalFetch: Proof.Single[
    Rental.fetch.type & Relation.Single[Rental.Id, Rental],
    Rental.Id,
    Rental
  ] = Gen.relationSingle(Rental.fetch) { id =>
    for {
      bookId <- gen.bookId
      userId <- gen.userId
    } yield Rental(id, bookId, userId)
  }

  implicit val rentalBook: Proof.Single[
    Rental.book.type & Relation.Single[Rental, Book],
    Rental,
    Book
  ] = Gen.relationSingle(Rental.book) { rental =>
    Gen.const(Book(rental.bookId, Some(rental.id)))
  }

  implicit val rentalUser: Proof.Single[
    Rental.user.type & Relation.Single[Rental, User],
    Rental,
    User
  ] = Gen.relationSingle(Rental.user) { rental =>
    Gen.const(User(rental.userId, List(rental.id)))
  }

  implicit val userCurrentRentals: Proof.Many[
    User.currentRentals.type & Relation.Many[User, List, Rental],
    User,
    Rental,
    List
  ] = Gen.relationMany(User.currentRentals) { user =>
    Gen
      // Use `expand` even when implementing other relations
      .listOf(gen.rentalId.expand(Rental.fetch))
      // Make it consistent
      .map(_.map(_.copy(userId = user.id)))
  }

  private val staticRentalId = Rental.Id("foo")

  property("Simple relation") = forAll {
    val relation       = Rental.fetch
    val staticRentalId = Rental.Id("foo")
    Gen.const(staticRentalId).expand(relation)
  } { rental =>
    rental.id == staticRentalId
  }

  property("Composing with &") = forAll {
    val relation       = Rental.fetch & Rental.fetch
    val staticRentalId = Rental.Id("foo")
    Gen.const(staticRentalId).expand(relation)
  } { case (rental1, rental2) =>
    (rental1.id == staticRentalId) &&
    (rental2.id == staticRentalId) &&
    (rental1 != rental2)
  }
  property("Composing with >>:") = forAll {
    val relation    = Rental.fetch >>: Rental.book
    val rentalIdGen = Gen.const(staticRentalId)
    rentalIdGen.expand(relation)
  } { (book: Book) =>
    book.currentRental contains staticRentalId
  }

  property("Composing with :>:") = forAll {
    val relation    = Rental.fetch :>: Rental.book
    val rentalIdGen = Gen.const(staticRentalId)
    rentalIdGen.expand(relation)
  } { case (rental, book) =>
    (rental.id == staticRentalId) &&
    (book.currentRental contains staticRentalId)
  }

  property("Complex relations") = forAll {
    val relation = Rental.fetch >>: Rental.user >>: (User.self & User.currentRentals)

    gen.rentalId.expand(relation)
  } { case (user, rentals) =>
    // Note the current limitation -- the context is only applied to one level,
    // and is forgotten as we go down one level deeper
    rentals.map(_.userId).forall(userId => userId == user.id)
  }
}
