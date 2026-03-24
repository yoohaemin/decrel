/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import decrel.syntax.RelationComposeSyntax
import zio.*
import zio.test.*

object zqueryNextSyntaxSpec extends ZIOSpecDefault {

  object syntax extends decrel.reify.zquery[Any] with zqueryNextSyntax[Any]
  import syntax.*

  case class Rental(id: Rental.Id, bookId: Book.Id, userId: User.Id)
  object Rental {
    case class Id(value: String)

    case object book extends Relation.Single[Rental, Book]
  }
  case class Book(id: Book.Id)
  object Book {
    case class Id(value: String)

    case object fetch extends Relation.Single[Book.Id, Book]
  }
  case class User(id: User.Id)
  object User {
    case class Id(value: String)

    case object fetch          extends Relation.Single[User.Id, User]
    case object currentRentals extends Relation.Many[User, Chunk, Rental]
  }

  case class State(rentals: Chunk[Rental], books: Chunk[Book], users: Chunk[User])

  case class Calls(
    value: Chunk[(?, Chunk[?])] = Chunk.empty
  ) {
    def add[Rel, From, To](rel: Rel & Relation.Declared[From, To], request: Chunk[From]): Calls =
      Calls(value.appended((rel, request)))
  }
  object Calls {
    def apply(as: (?, Chunk[?])*): Calls =
      Calls(as.to(Chunk))
  }

  case class TestError(msg: String = "")

  private val rental2 = Rental(Rental.Id("rental2"), Book.Id("book2"), User.Id("user2"))
  private val rental3 = Rental(Rental.Id("rental3"), Book.Id("book3"), User.Id("user2"))
  private val book1   = Book(Book.Id("book1"))
  private val book2   = Book(Book.Id("book2"))
  private val book3   = Book(Book.Id("book3"))
  private val user2   = User(User.Id("user2"))

  private val state = State(
    rentals = Chunk(rental2, rental3),
    books = Chunk(
      book1,
      book2,
      book3
    ),
    users = Chunk(user2)
  )

  class proofs(state: State, val calls: Ref[Calls]) {

    implicit val bookFetch: Proof.Single[
      Book.fetch.type & Relation.Single[Book.Id, Book],
      Book.Id,
      TestError,
      Book
    ] = implementSingleDatasource(Book.fetch) { ins =>
      calls.update(_.add(Book.fetch, ins)).flatMap { _ =>
        ZIO.foreach(
          ins.map(id => id -> state.books.find(_.id == id))
        ) { case (id, book) =>
          ZIO.fromOption(book).map(id -> _).mapError(_ => TestError(s"$id not found"))
        }
      }
    }

    implicit val userFetch: Proof.Single[
      User.fetch.type & Relation.Single[User.Id, User],
      User.Id,
      TestError,
      User
    ] = implementSingleDatasource(User.fetch) { ins =>
      calls.update(_.add(User.fetch, ins)).flatMap { _ =>
        ZIO.foreach(
          ins.map(id => id -> state.users.find(_.id == id))
        ) { case (id, user) =>
          ZIO.fromOption(user).map(id -> _).mapError(_ => TestError(s"$id not found"))
        }
      }
    }

    implicit val userCurrentRentals: Proof.Many[
      User.currentRentals.type & Relation.Many[User, Chunk, Rental],
      User,
      Nothing,
      Chunk,
      Rental
    ] =
      implementManyDatasource(User.currentRentals) { ins =>
        calls.update(_.add(User.currentRentals, ins)).map { _ =>
          ins.map(user =>
            user -> state.rentals.collect { case rental if rental.userId == user.id => rental }
          )
        }
      }

    implicit val rentalBook: Proof.Single[
      Rental.book.type & Relation.Single[Rental, Book],
      Rental,
      TestError,
      Book
    ] = contramapOneProof(bookFetch, Rental.book, _.bookId)
  }

  private val proofs = Ref.make(Calls()).map(new proofs(state, _))

  override def spec: Spec[TestEnvironment, Any] =
    suite("zqueryNext syntax")(
      test("expand for single relation") {
        proofs.flatMap { proofs =>
          import proofs.*

          for {
            result <- book1.id.expand(Book.fetch)
            calls  <- proofs.calls.get
          } yield assertTrue(
            result == book1,
            calls == Calls(Book.fetch -> Chunk(book1.id))
          )
        }
      },
      test("expand for composed many relation") {
        proofs.flatMap { proofs =>
          import proofs.*

          for {
            result <- user2.id.expand(User.fetch >>: User.currentRentals >>: Rental.book)
            calls  <- proofs.calls.get
          } yield assertTrue(
            result == Chunk(book2, book3),
            calls == Calls(
              User.fetch          -> Chunk(user2.id),
              User.currentRentals -> Chunk(user2),
              Book.fetch          -> Chunk(rental2.bookId, rental3.bookId)
            )
          )
        }
      },
      test("expandQuery returns zquery for single relation") {
        proofs.flatMap { proofs =>
          import proofs.*

          for {
            query  <- ZIO.succeed(book1.id.expandQuery(Book.fetch))
            before <- proofs.calls.get
            result <- query.run
            after  <- proofs.calls.get
          } yield assertTrue(
            before == Calls(),
            result == book1,
            after == Calls(Book.fetch -> Chunk(book1.id))
          )
        }
      },
      test("zqueryNext keeps base zquery syntax") {
        proofs.flatMap { proofs =>
          import proofs.*

          for {
            result <- Book.fetch.toQuery(book1.id).run
            calls  <- proofs.calls.get
          } yield assertTrue(
            result == book1,
            calls == Calls(Book.fetch -> Chunk(book1.id))
          )
        }
      }
    )
}
