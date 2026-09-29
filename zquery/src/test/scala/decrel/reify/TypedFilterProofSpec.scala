/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import decrel.filter.{ Path, Predicate }
import decrel.reify.zquery.*
import zio.*
import zio.test.*

object TypedFilterProofSpec extends ZIOSpecDefault {
  case class Customer(id: Int, budget: Int)
  case class Book(price: Int)
  case object Books        extends Relation.Many[Customer, List, Book]
  case object MaybeBook    extends Relation.Optional[Customer, Book]
  case object RequiredBook extends Relation.Single[Int, Book]
  case object CustomerBook extends Relation.Single[Customer, Book]

  type BooksProof = Proof.Many[
    Books.type & Relation.Many[Customer, List, Book],
    Customer,
    String,
    List,
    Book,
    Predicate[Customer, Book]
  ]
  type MaybeProof = Proof.Optional[
    MaybeBook.type & Relation.Optional[Customer, Book],
    Customer,
    Nothing,
    Book,
    Predicate[Customer, Book]
  ]
  type BaseProof =
    Proof.Single[RequiredBook.type & Relation.Single[Int, Book], Int, Nothing, Book, Predicate[
      Int,
      Book
    ]]
  type CustomerProof = Proof.Single[
    CustomerBook.type & Relation.Single[Customer, Book],
    Customer,
    Nothing,
    Book,
    Nothing
  ]

  def baseProof: BaseProof =
    implementFilteredSingleDatasource[RequiredBook.type, Int, Nothing, Book](RequiredBook) {
      (inputs, _) => ZIO.succeed(inputs.map(id => id -> Option(Book(id))))
    }

  private val price      = new Path[Book, Int](Vector("price"))
  private val budget     = new Path[Customer, Int](Vector("budget"))
  private val affordable = Predicate.build[Customer, Book]((in, out) => out(price) <= in(budget))

  override def spec: Spec[TestEnvironment, Any] = suite("typed filter implementations")(
    test("a batch retains each original input and its candidate output shape") {
      val customers = List(Customer(1, 5), Customer(1, 10))
      for {
        calls  <- Ref.make(List.empty[(Chunk[Customer], Option[Predicate[Customer, Book]])])
        result <- {
          implicit val proof: BooksProof =
            implementFilteredManyDatasource[Books.type, Customer, String, List, Book](Books) {
              (inputs, predicate) =>
                calls.update(_ :+ (inputs -> predicate)) *>
                  (if (predicate.contains(affordable))
                     ZIO.succeed(
                       inputs.map(customer =>
                         customer -> List(Book(3), Book(8)).filter(_.price <= customer.budget)
                       )
                     )
                   else ZIO.fail("Unexpected predicate"))
            }
          Books.filter(affordable).startingFrom(customers)
        }
        recorded <- calls.get
      } yield assertTrue(
        result == List(List(Book(3)), List(Book(3), Book(8))),
        recorded == List(Chunk.fromIterable(customers) -> Some(affordable))
      )
    },
    test("filtered optional access retains None entries and unfiltered access") {
      for {
        seen   <- Ref.make(List.empty[Option[Predicate[Customer, Book]]])
        result <- {
          implicit val proof: MaybeProof =
            implementFilteredOptionalDatasource[MaybeBook.type, Customer, Nothing, Book](MaybeBook) {
              (inputs, predicate) =>
                seen
                  .update(_ :+ predicate)
                  .as(
                    inputs.map(customer =>
                      customer -> (if (predicate.isEmpty || customer.budget >= 8) Some(Book(8))
                                   else None)
                    )
                  )
            }
          for {
            filtered <- MaybeBook
              .filter(affordable)
              .startingFrom(List(Customer(1, 5), Customer(2, 10)))
            unfiltered <- MaybeBook.startingFrom(Customer(1, 5))
          } yield (filtered, unfiltered)
        }
        recorded <- seen.get
      } yield assertTrue(
        result == (List(None, Some(Book(8))), Some(Book(8))),
        recorded == List(Some(affordable), None)
      )
    },
    test(
      "equal predicates share requests, different predicates on the same input remain separate"
    ) {
      val customer = Customer(1, 5)
      for {
        calls  <- Ref.make(List.empty[Option[Predicate[Customer, Book]]])
        result <- {
          implicit val proof: BooksProof =
            implementFilteredManyDatasource[Books.type, Customer, String, List, Book](Books) {
              (inputs, predicate) =>
                calls.update(_ :+ predicate) *>
                  (predicate match {
                    case Some(p) if p == affordable  => ZIO.succeed(inputs.map(_ -> List(Book(3))))
                    case Some(p) if p == !affordable => ZIO.succeed(inputs.map(_ -> List(Book(8))))
                    case _                           => ZIO.fail("Unexpected predicate")
                  })
            }
          val first     = Books.filter((in, out) => out(price) <= in(budget))
          val equal     = Books.filter((in, out) => out(price) <= in(budget))
          val different = Books.filter(!affordable)
          first
            .startingFromQuery(customer)
            .zipPar(equal.startingFromQuery(customer))
            .zipPar(different.startingFromQuery(customer))
            .run
        }
        recorded <- calls.get
      } yield assertTrue(
        result == (List(Book(3)), List(Book(3)), List(Book(8))),
        recorded.size == 2,
        recorded.toSet == Set[Option[Predicate[Customer, Book]]](
          Some(affordable),
          Some(!affordable)
        )
      )
    },
    test("function contramap still reuses the unfiltered implementation") {
      for {
        seen   <- Ref.make(List.empty[Option[Predicate[Int, Book]]])
        result <- {
          val base: BaseProof =
            implementFilteredSingleDatasource[RequiredBook.type, Int, Nothing, Book](RequiredBook) {
              (inputs, predicate) =>
                seen.update(_ :+ predicate).as(inputs.map(id => id -> Some(Book(id))))
            }
          implicit val derived: CustomerProof =
            base.contramap(CustomerBook)((customer: Customer) => customer.id)
          CustomerBook.startingFrom(Customer(7, 10))
        }
        recorded <- seen.get
      } yield assertTrue(result == Book(7), recorded == List(None))
    },
    test("unfiltered access through a filter-aware single honors preloaded row values") {
      for {
        calls  <- Ref.make(List.empty[Chunk[Int]])
        result <- {
          implicit val proof: BaseProof =
            implementFilteredSingleDatasource[RequiredBook.type, Int, Nothing, Book](RequiredBook) {
              (inputs, _) => calls.update(_ :+ inputs).as(inputs.map(id => id -> Some(Book(id))))
            }
          RequiredBook.startingFrom(List(7, 8), Cache.empty.add(RequiredBook, 7, Book(42)))
        }
        recorded <- calls.get
      } yield assertTrue(result == List(Book(42), Book(8)), recorded == List(Chunk(8)))
    },
    test("an equal filtered relation can consume preloaded optional rows") {
      val predicate = Predicate.build[Int, Book]((_, out) => out(price) > 0)
      val cache     = Cache.empty.add(RequiredBook.filter(predicate), 1, Some(Book(42)))
      for {
        calls  <- Ref.make(0)
        result <- {
          implicit val proof: BaseProof =
            implementFilteredSingleDatasource[RequiredBook.type, Int, Nothing, Book](RequiredBook) {
              (inputs, _) => calls.update(_ + 1).as(inputs.map(id => id -> Option(Book(id))))
            }
          RequiredBook.filter((_, out) => out(price) > 0).startingFrom(1, cache)
        }
        count <- calls.get
      } yield assertTrue(result == Some(Book(42)), count == 0)
    },
    test("queries from separate module instances keep their implementations") {
      val first      = new decrel.reify.zquery[Any] {}
      val second     = new decrel.reify.zquery[Any] {}
      val firstProof =
        first.implementSingleDatasource[RequiredBook.type, Int, Nothing, Book](RequiredBook) {
          (inputs, _) => ZIO.succeed(inputs.map(_ -> Book(1)))
        }
      val secondProof =
        second.implementSingleDatasource[RequiredBook.type, Int, Nothing, Book](RequiredBook) {
          (inputs, _) => ZIO.succeed(inputs.map(_ -> Book(2)))
        }
      firstProof
        .reify(RequiredBook)
        .apply(0)
        .zipPar(secondProof.reify(RequiredBook).apply(0))
        .run
        .map(result => assertTrue(result == (Book(1), Book(2))))
    },
    test("integration contramap helpers retain unfiltered access") {
      typeCheck("""import decrel._
        import decrel.reify.zquery._
        import decrel.reify.TypedFilterProofSpec._
        val base: BaseProof = baseProof
        implicit val derived: CustomerProof = contramapOneProof(base, CustomerBook, (customer: Customer) => customer.id)
        CustomerBook.startingFrom(Customer(1, 5))
      """).map(result => assertTrue(result.isRight))
    },
    test("function contramap cannot derive filtered access") {
      typeCheck("""import decrel._
        import decrel.filter._
        import decrel.reify.zquery._
        import decrel.reify.TypedFilterProofSpec._
        val base: BaseProof = baseProof
        implicit val derived: CustomerProof = base.contramap(CustomerBook)((customer: Customer) => customer.id)
        CustomerBook.filter(Predicate.always[Customer, Book]).startingFrom(Customer(1, 5))
      """).map(result => assertTrue(result.isLeft))
    },
    test("the integration contramap helper cannot derive filtered access") {
      typeCheck("""import decrel._
        import decrel.filter._
        import decrel.reify.zquery._
        import decrel.reify.TypedFilterProofSpec._
        val base: BaseProof = baseProof
        implicit val derived: CustomerProof = contramapOneProof(base, CustomerBook, (customer: Customer) => customer.id)
        CustomerBook.filter(Predicate.always[Customer, Book]).startingFrom(Customer(1, 5))
      """).map(result => assertTrue(result.isLeft))
    }
  )
}
