/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import decrel.reify.zquery.*
import zio.*
import zio.test.*
import zio.test.Assertion.*

object zqueryProofSpec extends ZIOSpecDefault {

  case class Rental(id: Rental.Id, bookId: Book.Id, userId: User.Id)
  object Rental {
    case class Id(value: String)

    case object fetch extends Relation.Single[Rental.Id, Rental]
    case object self  extends Relation.Self[Rental]
    case object book  extends Relation.Single[Rental, Book]
    case object user  extends Relation.Single[Rental, User]
  }
  case class Book(id: Book.Id)
  object Book {
    case class Id(value: String)

    case object fetch         extends Relation.Single[Book.Id, Book]
    case object self          extends Relation.Self[Book]
    case object currentRental extends Relation.Optional[Book, Rental]
  }
  case class User(id: User.Id)
  object User {
    case class Id(value: String)

    case object fetch          extends Relation.Single[User.Id, User]
    case object self           extends Relation.Self[User]
    case object currentRentals extends Relation.Many[User, Chunk, Rental]
  }

  case class State(rentals: Chunk[Rental], books: Chunk[Book], users: Chunk[User])
  object State {
    val empty: State = State(Chunk.empty, Chunk.empty, Chunk.empty)
  }

  case class Calls(
    value: Chunk[(?, Chunk[?])] = Chunk.empty
  ) {
    def add[Rel, From, To](rel: Rel & Relation.Declared[From, To], request: Chunk[From]): Calls =
      Calls(value.appended((rel, request)))
  }
  object Calls {
    def apply(as: (?, Chunk[?])*): Calls =
      Calls(as.to(Chunk))

    val empty: Calls = Calls()
  }

  case class TestError(msg: String = "")

  private val rental1 = Rental(Rental.Id("rental1"), Book.Id("book1"), User.Id("user1"))
  private val rental2 = Rental(Rental.Id("rental2"), Book.Id("book2"), User.Id("user2"))
  private val rental3 = Rental(Rental.Id("rental3"), Book.Id("book3"), User.Id("user2"))
  private val book1   = Book(Book.Id("book1"))
  private val book2   = Book(Book.Id("book2"))
  private val book3   = Book(Book.Id("book3"))
  private val book4   = Book(Book.Id("book4"))
  private val user1   = User(User.Id("user1"))
  private val user2   = User(User.Id("user2"))
  private val user3   = User(User.Id("user3"))

  private val state = State(
    rentals = Chunk(rental1, rental2, rental3),
    books = Chunk(
      Book(Book.Id("book1")),
      Book(Book.Id("book2")),
      Book(Book.Id("book3")),
      Book(Book.Id("book4"))
    ),
    users = Chunk(User(User.Id("user1")), User(User.Id("user2")), User(User.Id("user3")))
  )

  class proofs(state: State, val calls: Ref[Calls]) {

    implicit val rentalFetch: Proof.Single[
      Rental.fetch.type & Relation.Single[Rental.Id, Rental],
      Rental.Id,
      TestError,
      Rental
    ] =
      implementSingleDatasource(Rental.fetch) { ins =>
        calls.update(_.add(Rental.fetch, ins)).flatMap { _ =>
          ZIO.foreach(
            ins.map(id => id -> state.rentals.find(_.id == id))
          ) { case (id, rental) =>
            ZIO.fromOption(rental).map(id -> _).mapError(_ => TestError(s"$id not found"))
          }
        }
      }

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

    implicit val rentalBook: Proof.Single[
      Rental.book.type & Relation.Single[Rental, Book],
      Rental,
      TestError,
      Book
    ] = contramapOneProof(bookFetch, Rental.book, _.bookId)

    implicit val rentalUser: Proof.Single[
      Rental.user.type & Relation.Single[Rental, User],
      Rental,
      TestError,
      User
    ] = contramapOneProof(userFetch, Rental.user, _.userId)

    implicit val bookCurrentRental: Proof.Optional[
      Book.currentRental.type & Relation.Optional[Book, Rental],
      Book,
      Nothing,
      Rental
    ] = implementOptionalDatasource(Book.currentRental) { ins =>
      calls.update(_.add(Book.currentRental, ins)).map { _ =>
        ins.map(book =>
          book -> state.rentals.collectFirst {
            case rental if rental.bookId == book.id => rental
          }
        )
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
  }

  private val proofs = Ref.make(Calls.empty).map(new proofs(state, _))

  /**
   * Testing is for
   * - Correctness
   *
   * and if applicable,
   * - Batching
   * - Deduplication
   * - Caching
   * as well
   */
  override def spec: Spec[Environment, Any] =
    suite("ZQuery proof")(
      suite("Declared relations")(
        test("Self relation") {
          // self does not require any proof
          val relation = Rental.self
          val result   = relation.startingFrom(rental1)

          assertZIO(result)(equalTo(rental1))
        },
        test("Single relation - implemented for itself") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.fetch
            val result   = relation.startingFrom(book1.id)

            assertZIO(result)(equalTo(book1)) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.fetch -> Chunk(rental1.bookId))))
          }
        },
        test("Single relation - contramapped") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Rental.book
            val result   = relation.startingFrom(rental1)

            assertZIO(result)(equalTo(book1)) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.fetch -> Chunk(rental1.bookId))))
          }
        },
        test("Optional relation - existing") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.currentRental
            val result   = relation.startingFrom(book1)

            assertZIO(result)(isSome(equalTo(rental1))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.currentRental -> Chunk(book1))))
          }
        },
        test("Optional relation - non-existing") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.currentRental
            val result   = relation.startingFrom(book4)

            assertZIO(result)(isNone) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.currentRental -> Chunk(book4))))
          }
        },
        test("Many relation") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals
            val result   = relation.startingFrom(user1)

            assertZIO(result)(equalTo(Chunk(rental1))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(User.currentRentals -> Chunk(user1))))
          }
        },
        test("Many relation - multiple results") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals
            val result   = relation.startingFrom(user2)

            assertZIO(result)(equalTo(Chunk(rental2, rental3))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(User.currentRentals -> Chunk(user2))))
          }
        },
        test("Many relation - empty result") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals
            val result   = relation.startingFrom(user3)

            assertZIO(result)(equalTo(Chunk.empty)) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(User.currentRentals -> Chunk(user3))))
          }
        },
        test("Many relation with batch queries") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals
            val result   = relation.startingFrom(Chunk(user1, user2, user3))

            assertZIO(result)(
              equalTo(Chunk(Chunk(rental1), Chunk(rental2, rental3), Chunk.empty))
            ) &&
            assertZIO(proofs.calls.get)(
              equalTo(Calls(User.currentRentals -> Chunk(user1, user2, user3)))
            )
          }
        },
        test("Many relation with cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(User.currentRentals, user2, Chunk(rental2, rental3))

            val relation = User.currentRentals
            val result   = relation.startingFrom(user2, cache)

            assertZIO(result)(equalTo(Chunk(rental2, rental3))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls()))
          }
        }
      ),
      suite("Cache operations")(
        test("Single relation with cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.fetch, book1.id, book1)

            val relation = Book.fetch
            val result   = relation.startingFrom(book1.id, cache)

            assertZIO(result)(equalTo(book1)) &&
            assertZIO(proofs.calls.get)(equalTo(Calls()))
          }
        },
        test("Optional relation with cache - existing") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.currentRental, book1, Some(rental1))

            val relation = Book.currentRental
            val result   = relation.startingFrom(book1, cache)

            assertZIO(result)(isSome(equalTo(rental1))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls()))
          }
        },
        test("Optional relation with cache - non-existing") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.currentRental, book4, None)

            val relation = Book.currentRental
            val result   = relation.startingFrom(book4, cache)

            assertZIO(result)(isNone) &&
            assertZIO(proofs.calls.get)(equalTo(Calls()))
          }
        },
        test("Multiple cache entries") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.fetch, book1.id, book1)
              .add(User.fetch, user1.id, user1)
              .add(Rental.fetch, rental1.id, rental1)

            val bookRelation   = Book.fetch
            val userRelation   = User.fetch
            val rentalRelation = Rental.fetch

            val result = for {
              book   <- bookRelation.startingFrom(book1.id, cache)
              user   <- userRelation.startingFrom(user1.id, cache)
              rental <- rentalRelation.startingFrom(rental1.id, cache)
            } yield (book, user, rental)

            assertZIO(result)(equalTo((book1, user1, rental1))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls()))
          }
        }
      ),
      suite("Composed relations with cache")(
        test("startingFrom with cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.fetch, book1.id, book1)
              .add(Book.fetch, book2.id, book2)

            val relation = Book.fetch
            val result   = relation.startingFrom(List(book1.id, book2.id, book3.id), cache)

            assertZIO(result)(equalTo(List(book1, book2, book3))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.fetch -> Chunk(book3.id))))
          }
        },
        test(">>: composition with cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.currentRental, book1, Some(rental1))
              .add(User.fetch, rental1.userId, user1)

            val relation = Book.currentRental >>: Rental.user
            val result   = relation.startingFrom(book1, cache)

            assertZIO(result)(isSome(equalTo(user1))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls()))
          }
        },
        test("complex composition with partial cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(User.fetch, user2.id, user2)
              // Note: We don't cache User.currentRentals, so that will still be called
              .add(Book.fetch, book2.id, book2)
              .add(Book.fetch, book3.id, book3)

            val relation = User.fetch >>: User.currentRentals >>: Rental.book
            val result   = relation.startingFrom(user2.id, cache)

            assertZIO(result)(equalTo(Chunk(book2, book3))) &&
            assertZIO(proofs.calls.get)(
              equalTo(
                Calls(
                  User.currentRentals -> Chunk(user2)
                )
              )
            )
          }
        },
        test("many relation composition with cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(User.currentRentals, user2, Chunk(rental2, rental3))
              .add(Book.fetch, book2.id, book2)
            // Note: book3.id isn't cached

            val relation = User.currentRentals >>: Rental.book
            val result   = relation.startingFrom(user2, cache)

            assertZIO(result)(equalTo(Chunk(book2, book3))) &&
            assertZIO(proofs.calls.get)(
              equalTo(
                Calls(
                  Book.fetch -> Chunk(book3.id)
                )
              )
            )
          }
        }
      ),
      suite("Unique datasource names")(
        test("Two relations with the same name") {
          proofs.flatMap { proofs =>
            import proofs.*

            val fetchUser = User.fetch
            val user      = fetchUser.startingFromQuery(user1.id)

            val fetchBook = Book.fetch
            val book      = fetchBook.startingFromQuery(book1.id)

            assertZIO((user <*> book).run)(equalTo((user1, book1)))
          }
        }
      )
    )

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] = testEnvironment
}
