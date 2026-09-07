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

  private val customBookFetch = Book.fetch.customImpl

  case class State(rentals: Chunk[Rental], books: Chunk[Book], users: Chunk[User])
  object State {
    val empty: State = State(Chunk.empty, Chunk.empty, Chunk.empty)
  }

  case class Calls(
    value: Chunk[(?, Chunk[?])] = Chunk.empty
  ) {
    def add[Rel, From, To](rel: Rel & Relation[From, To], request: Chunk[From]): Calls =
      Calls(value.appended((rel, request)))
  }
  object Calls {
    def apply(as: (?, Chunk[?])*): Calls =
      Calls(as.to(Chunk))

    val empty: Calls = Calls()
  }

  case class TestError(msg: String = "")
  case class BookFilter(prefix: String)

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
      Rental,
      Nothing
    ] =
      implementSingleDatasource[Rental.fetch.type, Rental.Id, TestError, Rental](Rental.fetch) { (ins, _: Option[Nothing]) =>
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
      Book,
      Nothing
    ] = implementSingleDatasource[Book.fetch.type, Book.Id, TestError, Book](Book.fetch) { (ins, _: Option[Nothing]) =>
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
      User,
      Nothing
    ] = implementSingleDatasource[User.fetch.type, User.Id, TestError, User](User.fetch) { (ins, _: Option[Nothing]) =>
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
      Book,
      Nothing
    ] = contramapOneProof[
      Book.fetch.type & Relation.Single[Book.Id, Book],
      Rental.book.type & Relation.Single[Rental, Book],
      Book.Id,
      TestError,
      Book,
      Rental,
      Nothing
    ](bookFetch, Rental.book, _.bookId)

    implicit val rentalUser: Proof.Single[
      Rental.user.type & Relation.Single[Rental, User],
      Rental,
      TestError,
      User,
      Nothing
    ] = contramapOneProof[
      User.fetch.type & Relation.Single[User.Id, User],
      Rental.user.type & Relation.Single[Rental, User],
      User.Id,
      TestError,
      User,
      Rental,
      Nothing
    ](userFetch, Rental.user, _.userId)

    implicit val bookCurrentRental: Proof.Optional[
      Book.currentRental.type & Relation.Optional[Book, Rental],
      Book,
      Nothing,
      Rental,
      Nothing
    ] = implementOptionalDatasource[Book.currentRental.type, Book, Nothing, Rental](Book.currentRental) { (ins, _: Option[Nothing]) =>
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
      Rental,
      Nothing
    ] =
      implementManyDatasource[User.currentRentals.type, User, Nothing, Chunk, Rental](User.currentRentals) { (ins, _: Option[Nothing]) =>
        calls.update(_.add(User.currentRentals, ins)).map { _ =>
          ins.map(user =>
            user -> state.rentals.collect { case rental if rental.userId == user.id => rental }
          )
        }
      }

    implicit val customBookFetchProof: Proof[
      Relation.Custom[Book.fetch.type & Relation.Single[Book.Id, Book], Book.Id, Book],
      Book.Id,
      TestError,
      Book,
      Nothing
    ] =
      implementCustomDatasource[Book.fetch.type & Relation.Single[Book.Id, Book], Book.Id, TestError, Book](customBookFetch) { (ins, _: Option[Nothing]) =>
        calls.update(_.add(customBookFetch, ins)).flatMap { _ =>
          ZIO.foreach(
            ins.map(id => id -> state.books.find(_.id == id))
          ) { case (id, book) =>
            ZIO.fromOption(book).map(id -> _).mapError(_ => TestError(s"$id not found"))
          }
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

          for {
            result <- relation.startingFrom(rental1)
          } yield assertTrue(
            result == rental1
          )
        },
        test("Legacy single-value alias") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.fetch

            for {
              result <- relation.toZIO(book1.id)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == book1,
              calls == Calls(Book.fetch -> Chunk(book1.id))
            )
          }
        },
        test("Legacy query alias") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.fetch

            for {
              result <- relation.toQuery(book1.id).run
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == book1,
              calls == Calls(Book.fetch -> Chunk(book1.id))
            )
          }
        },
        test("Single relation - implemented for itself") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.fetch
            for {
              result <- relation.startingFrom(book1.id)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == book1,
              calls == Calls(Book.fetch -> Chunk(book1.id))
            )
          }
        },
        test("Single relation - contramapped") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Rental.book

            for {
              result <- relation.startingFrom(rental1)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == book1,
              calls == Calls(Book.fetch -> Chunk(rental1.bookId))
            )
          }
        },
        test("Optional relation - existing") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.currentRental

            for {
              result <- relation.startingFrom(book1)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result.contains(rental1),
              calls == Calls(Book.currentRental -> Chunk(book1))
            )
          }
        },
        test("Optional relation - non-existing") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.currentRental

            for {
              result <- relation.startingFrom(book4)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result.isEmpty,
              calls == Calls(Book.currentRental -> Chunk(book4))
            )
          }
        },
        test("Many relation") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals

            for {
              result <- relation.startingFrom(user1)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk(rental1),
              calls == Calls(User.currentRentals -> Chunk(user1))
            )
          }
        },
        test("Many relation - multiple results") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals

            for {
              result <- relation.startingFrom(user2)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk(rental2, rental3),
              calls == Calls(User.currentRentals -> Chunk(user2))
            )
          }
        },
        test("Many relation - empty result") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals

            for {
              result <- relation.startingFrom(user3)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk.empty,
              calls == Calls(User.currentRentals -> Chunk(user3))
            )
          }
        },
        test("Many relation with batch queries") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals

            for {
              result <- relation.startingFrom(Chunk(user1, user2, user3))
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk(Chunk(rental1), Chunk(rental2, rental3), Chunk.empty),
              calls == Calls(User.currentRentals -> Chunk(user1, user2, user3))
            )
          }
        },
        test("Many relation with cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(User.currentRentals, user2, Chunk(rental2, rental3))

            val relation = User.currentRentals

            for {
              result <- relation.startingFrom(user2, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk(rental2, rental3),
              calls == Calls()
            )
          }
        },
        test("Custom relation - implemented for itself") {
          proofs.flatMap { proofs =>
            import proofs.*

            for {
              result <- customBookFetch.startingFrom(book1.id)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == book1,
              calls == Calls(customBookFetch -> Chunk(book1.id))
            )
          }
        },
        test("Single relation receives filters and still works unfiltered") {
          sealed trait BaseBookFilter
          case class ExactBookFilter(prefix: String) extends BaseBookFilter

          for {
            seenFilters <- Ref.make(Chunk.empty[Option[BaseBookFilter]])
            result <- {
              implicit val filteredBookFetch: Proof.Single[
                Book.fetch.type & Relation.Single[Book.Id, Book],
                Book.Id,
                TestError,
                Book,
                BaseBookFilter
              ] =
                implementSingleDatasource[Book.fetch.type, Book.Id, TestError, Book, BaseBookFilter](Book.fetch) { (ins, filter: Option[BaseBookFilter]) =>
                  seenFilters.update(_ :+ filter) *>
                    ZIO.foreach(
                      ins.map(id => id -> state.books.find(_.id == id))
                    ) { case (id, book) =>
                      ZIO.succeed(id -> book)
                    }
                }

              for {
                filtered   <- Book.fetch.filter(ExactBookFilter("book")).startingFrom(book1.id)
                unfiltered <- Book.fetch.startingFrom(book2.id)
              } yield (filtered, unfiltered)
            }
            filters <- seenFilters.get
          } yield assertTrue(
            result == (Some(book1), book2),
            filters == Chunk(Some(ExactBookFilter("book")), None)
          )
        },
        test("Filtered single composes like an optional relation") {
          sealed trait BaseBookFilter
          case class ExactBookFilter(prefix: String) extends BaseBookFilter

          proofs.flatMap { proofs =>
            import proofs.*

            implicit val filteredBookFetch: Proof.Single[
              Book.fetch.type & Relation.Single[Book.Id, Book],
              Book.Id,
              TestError,
              Book,
              BaseBookFilter
            ] =
              implementSingleDatasource[Book.fetch.type, Book.Id, TestError, Book, BaseBookFilter](Book.fetch) { (ins, _) =>
                proofs.calls.update(_.add(Book.fetch, ins)).as(ins.map(_ -> None))
              }

            val relation = Book.fetch.filter(ExactBookFilter("book")) >>: Book.currentRental

            for {
              result <- relation.startingFrom(book1.id)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == None,
              calls == Calls(Book.fetch -> Chunk(book1.id))
            )
          }
        },
        test("Filtered many composes like a many relation") {
          sealed trait RentalFilter
          case class ExactRentalFilter(id: Rental.Id) extends RentalFilter

          proofs.flatMap { proofs =>
            import proofs.*

            implicit val filteredCurrentRentals: Proof.Many[
              User.currentRentals.type & Relation.Many[User, Chunk, Rental],
              User,
              Nothing,
              Chunk,
              Rental,
              RentalFilter
            ] =
              implementManyDatasource[
                User.currentRentals.type,
                User,
                Nothing,
                Chunk,
                Rental,
                RentalFilter
              ](User.currentRentals) { (ins, filter) =>
                proofs.calls.update(_.add(User.currentRentals, ins)).map { _ =>
                  ins.map(user =>
                    user -> state.rentals.collect {
                      case rental
                          if rental.userId == user.id &&
                            filter.forall {
                              case ExactRentalFilter(expected) => rental.id == expected
                            } =>
                        rental
                    }
                  )
                }
              }

            val relation = User.currentRentals.filter(ExactRentalFilter(rental2.id)) >>: Rental.book

            for {
              result <- relation.startingFrom(user2)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk(book2),
              calls == Calls(
                User.currentRentals -> Chunk(user2),
                Book.fetch          -> Chunk(rental2.bookId)
              )
            )
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

            for {
              result <- relation.startingFrom(book1.id, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == book1,
              calls == Calls()
            )
          }
        },
        test("Optional relation with cache - existing") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.currentRental, book1, Some(rental1))

            val relation = Book.currentRental

            for {
              result <- relation.startingFrom(book1, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result.contains(rental1),
              calls == Calls()
            )
          }
        },
        test("Optional relation with cache - non-existing") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.currentRental, book4, None)

            val relation = Book.currentRental

            for {
              result <- relation.startingFrom(book4, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result.isEmpty,
              calls == Calls()
            )
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

            for {
              book   <- bookRelation.startingFrom(book1.id, cache)
              user   <- userRelation.startingFrom(user1.id, cache)
              rental <- rentalRelation.startingFrom(rental1.id, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              book == book1,
              user == user1,
              rental == rental1,
              calls == Calls()
            )
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

            for {
              result <- relation.startingFrom(List(book1.id, book2.id, book3.id), cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == List(book1, book2, book3),
              calls == Calls(Book.fetch -> Chunk(book3.id))
            )
          }
        },
        test(">>: composition with cache") {
          proofs.flatMap { proofs =>
            import proofs.*

            val cache = Cache.empty
              .add(Book.currentRental, book1, Some(rental1))
              .add(User.fetch, rental1.userId, user1)

            val relation = Book.currentRental >>: Rental.user

            for {
              result <- relation.startingFrom(book1, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result.contains(user1),
              calls == Calls()
            )
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

            for {
              result <- relation.startingFrom(user2.id, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk(book2, book3),
              calls == Calls(User.currentRentals -> Chunk(user2))
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

            for {
              result <- relation.startingFrom(user2, cache)
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == Chunk(book2, book3),
              calls == Calls(Book.fetch -> Chunk(book3.id))
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

            for {
              result <- (user <*> book).run
            } yield assertTrue(
              result == (user1, book1)
            )
          }
        },
        test("Filtered relations with different filters do not share a datasource") {
          sealed trait BookFilterById
          case class ExactBookIdFilter(id: Book.Id) extends BookFilterById

          val filter1 = ExactBookIdFilter(book1.id)
          val filter2 = ExactBookIdFilter(book2.id)

          for {
            calls <- Ref.make(Chunk.empty[(Option[BookFilterById], Chunk[Book.Id])])
            result <- {
              implicit val filteredBookFetch: Proof.Single[
                Book.fetch.type & Relation.Single[Book.Id, Book],
                Book.Id,
                Nothing,
                Book,
                BookFilterById
              ] =
                implementSingleDatasource[Book.fetch.type, Book.Id, Nothing, Book, BookFilterById](Book.fetch) { (ins, filter) =>
                  calls.update(_ :+ (filter -> ins)).as {
                    ins.flatMap { id =>
                      filter.collect {
                        case ExactBookIdFilter(expected) if expected == id =>
                          id -> Some(state.books.find(_.id == id).get)
                      }
                    }
                  }
                }

              (
                Book.fetch.filter(filter1).startingFromQuery(book1.id) <*>
                  Book.fetch.filter(filter2).startingFromQuery(book2.id)
              ).run
            }
            seenCalls <- calls.get
          } yield assertTrue(
            result == (Some(book1), Some(book2)),
            seenCalls.toSet == Set[(Option[BookFilterById], Chunk[Book.Id])](
              (Some(filter1), Chunk(book1.id)),
              (Some(filter2), Chunk(book2.id))
            )
          )
        },
        test("Distinct custom relations do not share a datasource") {
          case object LeftFetch  extends Relation.Single[Book.Id, Book]
          case object RightFetch extends Relation.Single[Book.Id, Book]

          val leftCustom  = LeftFetch.customImpl
          val rightCustom = RightFetch.customImpl

          for {
            calls <- Ref.make(Chunk.empty[(Relation.Custom[?, Book.Id, Book], Chunk[Book.Id])])
            result <- {
              implicit val leftProof: Proof[
                Relation.Custom[LeftFetch.type & Relation.Single[Book.Id, Book], Book.Id, Book],
                Book.Id,
                Nothing,
                Book,
                Nothing
              ] =
                implementCustomDatasource[
                  LeftFetch.type & Relation.Single[Book.Id, Book],
                  Book.Id,
                  Nothing,
                  Book
                ](leftCustom) { (ins, _: Option[Nothing]) =>
                  calls.update(_ :+ (leftCustom -> ins)).as {
                    ins.collect {
                      case id if id == book1.id => id -> book1
                    }
                  }
                }

              implicit val rightProof: Proof[
                Relation.Custom[RightFetch.type & Relation.Single[Book.Id, Book], Book.Id, Book],
                Book.Id,
                Nothing,
                Book,
                Nothing
              ] =
                implementCustomDatasource[
                  RightFetch.type & Relation.Single[Book.Id, Book],
                  Book.Id,
                  Nothing,
                  Book
                ](rightCustom) { (ins, _: Option[Nothing]) =>
                  calls.update(_ :+ (rightCustom -> ins)).as {
                    ins.collect {
                      case id if id == book2.id => id -> book2
                    }
                  }
                }

              (leftCustom.startingFromQuery(book1.id) <*> rightCustom.startingFromQuery(book2.id)).run
            }
            seenCalls <- calls.get
          } yield assertTrue(
            result == (book1, book2),
            seenCalls.toSet == Set[(Relation.Custom[?, Book.Id, Book], Chunk[Book.Id])](
              (leftCustom, Chunk(book1.id)),
              (rightCustom, Chunk(book2.id))
            )
          )
        }
      ),
      suite("Deduplication")(
        test("Single relation deduplication") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.fetch

            for {
              _ <- ZIO.unit
              // Create two queries for the same book ID
              results = relation.startingFromQuery(Chunk(book1.id, book1.id))

              // Run both queries together
              combined <- results.run
              calls    <- proofs.calls.get
            } yield assertTrue(
              combined == Chunk(book1, book1),
              // Only one call should be made despite two queries
              calls == Calls(Book.fetch -> Chunk(book1.id))
            )
          }
        },
        test("Many relation deduplication") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = User.currentRentals

            for {
              _ <- ZIO.unit
              // Create two queries for the same user
              result1 = relation.startingFromQuery(user2)
              result2 = relation.startingFromQuery(user2)
              // Run both queries together
              combined <- (result1 <*> result2).run
              calls    <- proofs.calls.get
            } yield assertTrue(
              combined == (Chunk(rental2, rental3), Chunk(rental2, rental3)),
              // Only one call should be made despite two queries
              calls == Calls(User.currentRentals -> Chunk(user2))
            )
          }
        },
        test("Composed relation deduplication") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation1 = Rental.book
            val relation2 = Rental.user

            for {
              _ <- ZIO.unit
              // Create two queries that will both need to fetch the same book
              book1Result = relation1.startingFromQuery(rental1)
              book2Result = relation1.startingFromQuery(rental1)
              // Also fetch the user for the same rental
              userResult = relation2.startingFromQuery(rental1)
              // Run all queries together
              combined <- (book1Result <*> book2Result <*> userResult).run
              calls    <- proofs.calls.get
            } yield assertTrue(
              combined == (book1, book1, user1),
              // Book.fetch and User.fetch should each be called only once
              calls == Calls(
                Book.fetch -> Chunk(rental1.bookId),
                User.fetch -> Chunk(rental1.userId)
              )
            )
          }
        },
        test("Batch deduplication") {
          proofs.flatMap { proofs =>
            import proofs.*
            val relation = Book.fetch

            for {
              _ <- ZIO.unit
              // Create two batch queries with overlapping IDs
              result1 = relation.startingFromQuery(Chunk(book1.id, book2.id))
              result2 = relation.startingFromQuery(Chunk(book2.id, book3.id))
              // Run both queries together
              combined <- (result1 <~> result2).run
              calls    <- proofs.calls.get
            } yield assertTrue(
              combined == (Chunk(book1, book2), Chunk(book2, book3)),
              // Each book ID should only be fetched once, even though book2.id appears twice
              calls == Calls(Book.fetch -> Chunk(book1.id, book2.id, book3.id))
            )
          }
        },
        test("Complex composition deduplication") {
          proofs.flatMap { proofs =>
            import proofs.*

            // Create two different compositions that will both need to fetch user2's rentals
            val path1 = User.fetch >>: User.currentRentals
            val path2 = User.fetch >>: User.currentRentals >>: Rental.book

            for {
              _ <- ZIO.unit
              // These queries will both need to fetch the same user and rentals
              result1 = path1.startingFromQuery(user2.id)
              result2 = path2.startingFromQuery(user2.id)
              // Run both queries together
              combined <- (result1 <*> result2).run
              calls    <- proofs.calls.get
            } yield assertTrue(
              combined._1 == Chunk(rental2, rental3),
              combined._2 == Chunk(book2, book3),
              // Each operation should happen exactly once despite being used in multiple paths
              calls == Calls(
                User.fetch          -> Chunk(user2.id),
                User.currentRentals -> Chunk(user2),
                Book.fetch          -> Chunk(rental2.bookId, rental3.bookId)
              )
            )
          }
        },
        test("Multiple layers of deduplication") {
          proofs.flatMap { proofs =>
            import proofs.*

            // Create several dependent queries that all involve rental1
            val bookQuery = Rental.book.startingFromQuery(rental1)
            val userQuery = Rental.user.startingFromQuery(rental1)

            // Create queries that would cause duplicate requests without deduplication
            val bookAgainQuery = Book.fetch.startingFromQuery(rental1.bookId)
            val userAgainQuery = User.fetch.startingFromQuery(rental1.userId)

            for {
              // Run all queries together - this tests whether ZQuery properly deduplicates
              // both the direct fetch requests and the ones derived from Rental
              result <- (bookQuery <*> userQuery <*> bookAgainQuery <*> userAgainQuery).run
              calls  <- proofs.calls.get
            } yield assertTrue(
              result == (book1, user1, book1, user1),
              // Book.fetch and User.fetch should each be called exactly once
              calls == Calls(
                Book.fetch -> Chunk(rental1.bookId),
                User.fetch -> Chunk(rental1.userId)
              )
            )
          }
        }
      )
    )

  override val bootstrap: ZLayer[Any, Any, TestEnvironment] = testEnvironment
}
