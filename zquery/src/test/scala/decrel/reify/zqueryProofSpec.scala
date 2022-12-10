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
      Rental,
      Chunk
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
          val result   = relation.toZIO(rental1)

          assertZIO(result)(equalTo(rental1))
        },
        test("Single relation - implemented for itself") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.fetch
            val result   = relation.toZIO(book1.id)

            assertZIO(result)(equalTo(book1)) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.fetch -> Chunk(rental1.bookId))))
          }
        },
        test("Single relation - contramapped") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Rental.book
            val result   = relation.toZIO(rental1)

            assertZIO(result)(equalTo(book1)) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.fetch -> Chunk(rental1.bookId))))
          }
        },
        test("Optional relation - existing") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.currentRental
            val result   = relation.toZIO(book1)

            assertZIO(result)(isSome(equalTo(rental1))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.currentRental -> Chunk(book1))))
          }
        },
        test("Optional relation - non-existing") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.currentRental
            val result   = relation.toZIO(book4)

            assertZIO(result)(isNone) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(Book.currentRental -> Chunk(book4))))
          }
        },
        test("Many relation") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals
            val result   = relation.toZIO(user1)

            assertZIO(result)(equalTo(Chunk(rental1))) &&
            assertZIO(proofs.calls.get)(equalTo(Calls(User.currentRentals -> Chunk(user1))))
          }
        }
      ),
      suite("Composed relations")(
        suite("&")(
          test("simple")(
            proofs.flatMap { proofs =>
              import proofs.*
              val relation = Rental.self & Rental.book & Rental.user
              val result   = relation.toZIO(rental1)

              assertZIO(result)(equalTo((rental1, book1, user1))) &&
              assertZIO(proofs.calls.get)(
                equalTo(
                  Calls(
                    Book.fetch -> Chunk(book1.id),
                    User.fetch -> Chunk(user1.id)
                  )
                )
              )
            }
          ),
          test("cached")(
            proofs.flatMap { proofs =>
              import proofs.*

              val cache = Cache.empty
                // Notice that the only implemented proof matters,
                // not the proof that is used
                // .add(Rental.book, rental1, book1)
                .add(Book.fetch, book1.id, book1)

              val relation = Rental.self & Rental.book
              val result   = relation.toZIO(rental1, cache)

              assertZIO(result)(equalTo((rental1, book1))) &&
              assertZIO(proofs.calls.get)(equalTo(Calls()))
            }
          )
        ),
        suite(">>:")(
          test("simple") {
            proofs.flatMap { proofs =>
              import proofs.*
              val relation = Book.currentRental >>: Rental.book
              val result   = relation.toZIO(book1)

              assertZIO(result)(isSome(equalTo(book1))) &&
              assertZIO(proofs.calls.get)(
                equalTo(
                  Calls(
                    Book.currentRental -> Chunk(book1),
                    Book.fetch         -> Chunk(rental1.bookId)
                  )
                )
              )
            }
          },
          test("cached") {
            proofs.flatMap { proofs =>
              import proofs.*

              val cache = Cache.empty
                .add(Book.fetch, book1.id, book1)

              val relation = Book.currentRental >>: Rental.book
              val result   = relation.toZIO(book1, cache)

              assertZIO(result)(isSome(equalTo(book1))) &&
              assertZIO(proofs.calls.get)(
                equalTo(
                  Calls(
                    Book.currentRental -> Chunk(book1)
                  )
                )
              )
            }
          }
        ),
        suite(":>:")(
          test("simple") {
            proofs.flatMap { proofs =>
              import proofs.*
              val relation = Book.currentRental :>: Rental.book
              val result   = relation.toZIO(book1)

              assertZIO(result)(isSome(equalTo((rental1, book1)))) &&
              assertZIO(proofs.calls.get)(
                equalTo(
                  Calls(
                    Book.currentRental -> Chunk(book1),
                    Book.fetch         -> Chunk(rental1.bookId)
                  )
                )
              )
            }
          },
          test("cached") {
            proofs.flatMap { proofs =>
              import proofs.*

              val cache = Cache.empty
                .add(Book.fetch, book1.id, book1)

              val relation = Book.currentRental :>: Rental.book
              val result   = relation.toZIO(book1, cache)

              assertZIO(result)(isSome(equalTo((rental1, book1)))) &&
              assertZIO(proofs.calls.get)(
                equalTo(
                  Calls(
                    Book.currentRental -> Chunk(book1)
                  )
                )
              )
            }
          }
        ),
        suite("Optimizations")(
          suite("deduplication")(
            test("&")(
              proofs.flatMap { proofs =>
                import proofs.*

                val relation = Rental.book & Rental.book
                val result   = relation.toZIO(rental1)

                assertZIO(result)(equalTo((book1, book1))) &&
                assertZIO(proofs.calls.get)(
                  equalTo(
                    // batching and deduplication
                    Calls(
                      Book.fetch -> Chunk(book1.id)
                    )
                  )
                )
              }
            ),
            test(">>:")(
              proofs.flatMap { proofs =>
                import proofs.*

                val relation = Rental.book >>: Book.currentRental >>: Rental.book >>: Book.currentRental
                val result = relation.toZIO(rental1)

                assertZIO(result)(isSome(isSome(equalTo(rental1)))) &&
                assertZIO(proofs.calls.get)(
                  equalTo(
                    Calls(
                      Book.fetch         -> Chunk(book1.id),
                      Book.currentRental -> Chunk(book1)
                    )
                  )
                )
              }
            )
          ),
          test("batching")(
            proofs.flatMap { proofs =>
              import proofs.*

              val relation = Rental.user >>: User.currentRentals >>: Rental.book
              val result   = relation.toZIO(rental2)

              assertZIO(result)(equalTo(Chunk(book2, book3))) &&
              assertZIO(proofs.calls.get)(
                equalTo(
                  Calls(
                    User.fetch          -> Chunk(rental2.userId),
                    User.currentRentals -> Chunk(user2),
                    // The following two calls have been batched, avoiding N+1 problem
                    Book.fetch -> Chunk(rental3.bookId, rental2.bookId)
                  )
                )
              )
            }
          )
        )
      )
    )
}
