/* SPDX-License-Identifier: MPL-2.0 */
package decrel.reify

import decrel.*
import decrel.filter.*
import decrel.filter.OrderBy.*
import decrel.selection.{ ReferenceSelection, Selection }
import decrel.selection.syntax.*
import zio.*
import zio.test.*

/** Executable-shaped example plus integration tests; no database dependency. */
object ZQuerySelectionSpec extends ZIOSpecDefault {
  case class Customer(id: Int, budget: Int)
  case class Book(id: Int, price: Int, rating: Option[Int])
  case object Books        extends Relation.Many[Customer, List, Book]
  case object MaybeBook    extends Relation.Optional[Customer, Book]
  case object RequiredBook extends Relation.Single[Customer, Book]
  case object Publisher    extends Relation.Single[Book, String]

  sealed trait Failure
  case class Rejected(error: FilterError) extends Failure
  case class NoBook(customer: Customer)   extends Failure

  // Tests live inside decrel, so they can construct Path directly. Applications
  // use the PR's ZIO Blocks Lens -> Path adapter; no changes to that adapter.
  val budget = new Path[Customer, Int](Vector("budget"))
  val price  = new Path[Book, Int](Vector("price"))
  val bookId = new Path[Book, Int](Vector("id"))
  val rating = new Path[Book, Option[Int]](Vector("rating"))

  val affordable     = Predicate.build[Customer, Book]((in, out) => out(price) <= in(budget))
  val expensiveFirst =
    OrderBy.build[Customer, Book]((_, out) => out(price).desc.thenBy(out(bookId).asc))

  val catalog = Vector(Book(1, 9, None), Book(2, 3, Some(7)), Book(3, 8, Some(9)))

  val reference = new ReferenceSelection((record, field) =>
    (record, field) match {
      case (c: Customer, "budget") => Right(c.budget)
      case (c: Customer, "id")     => Right(c.id)
      case (b: Book, "price")      => Right(b.price)
      case (b: Book, "id")         => Right(b.id)
      case (b: Book, "rating")     => Right(b.rating)
      case _ => Left(FilterError(Vector(field), "Unknown field for this backend"))
    }
  )

  class Backend(val calls: Ref[Vector[Selection.Plan[Customer, Book]]])
      extends zquerySelection[Any] {
    private def run(
      inputs: Chunk[Customer],
      request: Selection.Plan[Customer, Book],
      candidates: Customer => Vector[Book]
    ): ZIO[Any, Failure, Chunk[(Customer, Vector[Book])]] =
      ZIO.fromEither(reference.compile(request).left.map(Rejected.apply)).flatMap { program =>
        calls.update(_ :+ request) *>
          ZIO.foreach(inputs) { customer =>
            ZIO
              .fromEither(program(customer, candidates(customer)).left.map(Rejected.apply))
              .map(customer -> _)
          }
      }

    // A real storage backend would compile request to its native query here.
    // It sees filtering, ordering and the top-one demand in one value.
    implicit val booksSource: SelectionSource[Books.type, Customer, Failure, Book] =
      implementManySelection[Books.type, Customer, Failure, List, Book](Books)((inputs, request) =>
        run(inputs, request, _ => catalog)
      )((customer, _) => ZIO.fail(NoBook(customer)))

    implicit val maybeSource: SelectionSource[MaybeBook.type, Customer, Failure, Book] =
      implementOptionalSelection[MaybeBook.type, Customer, Failure, Book](MaybeBook)(
        (inputs, request) =>
          run(inputs, request, c => if (c.budget < 0) Vector.empty else catalog.take(1))
      )((customer, _) => ZIO.fail(NoBook(customer)))

    implicit val requiredSource: SelectionSource[RequiredBook.type, Customer, Failure, Book] =
      implementSingleSelection[RequiredBook.type, Customer, Failure, Book](RequiredBook)(
        (inputs, request) => run(inputs, request, _ => catalog.take(1))
      )((customer, _) => ZIO.fail(NoBook(customer)))

    // Optional compatibility bridge: ordinary Books and Books.filter(p) keep
    // using the PR's proofs, but share the same implementation function.
    implicit val ordinaryBooks: Proof.Many[
      Books.type & Relation.Many[Customer, List, Book],
      Customer,
      Failure,
      List,
      Book,
      Predicate[Customer, Book]
    ] = implementFilteredManyDatasource[Books.type, Customer, Failure, List, Book](Books) {
      (inputs, predicate) =>
        booksSource
          .execute(inputs, Selection.Plan(predicate = predicate))
          .map(_.map { case (in, rows) => in -> rows.toList })
    }

    implicit val publisher: Proof.Single[
      Publisher.type & Relation.Single[Book, String],
      Book,
      Nothing,
      String,
      Nothing
    ] = implementSingleDatasource[Publisher.type, Book, Nothing, String](Publisher) { (inputs, _) =>
      ZIO.succeed(inputs.map(b => b -> s"publisher-${b.id}"))
    }
  }

  private def backend: UIO[Backend] =
    Ref.make(Vector.empty[Selection.Plan[Customer, Book]]).map(new Backend(_))

  override def spec: Spec[TestEnvironment, Any] = suite("ZQuery selected relations")(
    test("filter, order and select separately for original inputs with the same ID") {
      backend.flatMap { implementation =>
        import implementation.*
        val selected = Books.filter(affordable).orderBy(expensiveFirst).headOption
        for {
          result   <- selected.toZIOMany(List(Customer(1, 5), Customer(1, 10)))
          recorded <- calls.get
        } yield assertTrue(result == List(Some(catalog(1)), Some(catalog(0))), recorded.size == 1)
      }
    },
    test("the reference evaluator really enforces stage order") {
      backend.flatMap { implementation =>
        import implementation.*
        Books
          .filter(affordable)
          .orderBy(expensiveFirst)
          .headOption
          .toZIO(Customer(1, 8))
          .map(result => assertTrue(result == Some(catalog(2))))
      }
    },
    test("headOption succeeds with None; head uses the backend's typed error") {
      backend.flatMap { implementation =>
        import implementation.*
        val customer = Customer(2, 0)
        val selected = Books.filter(affordable).orderBy(expensiveFirst)
        for {
          optional <- selected.headOption.toZIO(customer)
          required <- selected.head.toZIO(customer).either
        } yield assertTrue(optional.isEmpty, required == Left(NoBook(customer)))
      }
    },
    test("Optional.head uses the same implementation-owned failure contract") {
      backend.flatMap { implementation =>
        import implementation.*
        val customer = Customer(1, -1)
        MaybeBook.head
          .toZIO(customer)
          .either
          .map(result => assertTrue(result == Left(NoBook(customer))))
      }
    },
    test("a filtered Single can require a result") {
      backend.flatMap { implementation =>
        import implementation.*
        val customer = Customer(1, 0)
        RequiredBook
          .filter(affordable)
          .head
          .toZIO(customer)
          .either
          .map(result => assertTrue(result == Left(NoBook(customer))))
      }
    },
    test("headOption is an ordinary Optional relation for further composition") {
      backend.flatMap { implementation =>
        import implementation.*
        val selected   = Books.filter(affordable).orderBy(expensiveFirst).headOption
        val expression = selected >>: Publisher
        expression
          .toZIOMany(List(Customer(1, 8), Customer(2, 0)))
          .map(result => assertTrue(result == List(Some("publisher-3"), None)))
      }
    },
    test("ordering alone yields an ordered Vector, including explicit missing placement") {
      backend.flatMap { implementation =>
        import implementation.*
        Books
          .orderBy((_, out) => out(rating).descNullsLast.thenBy(out(bookId).asc))
          .toZIO(Customer(1, 10))
          .map(result => assertTrue(result == Vector(catalog(2), catalog(1), catalog(0))))
      }
    },
    test("equal complete plans deduplicate; ordering and cardinality differences do not") {
      backend.flatMap { implementation =>
        import implementation.*
        val customer             = Customer(1, 10)
        val first                = Books.orderBy((_, out) => out(price).asc).headOption
        val equal                = Books.orderBy((_, out) => out(price).asc).headOption
        val differentOrder       = Books.orderBy((_, out) => out(price).desc).headOption
        val differentCardinality = Books.orderBy((_, out) => out(price).asc).head
        for {
          _ <- first
            .toQuery(customer)
            .zipPar(equal.toQuery(customer))
            .zipPar(differentOrder.toQuery(customer))
            .zipPar(differentCardinality.toQuery(customer))
            .run
          recorded <- calls.get
        } yield assertTrue(recorded.size == 3, recorded.distinct.size == 3)
      }
    },
    test("separate interpreter instances do not share request identities") {
      for {
        a <- backend
        b <- backend
        selected = Books.orderBy(expensiveFirst).headOption
        qa       = { import a.*; selected.toQuery(Customer(1, 10)) }
        qb       = { import b.*; selected.toQuery(Customer(1, 10)) }
        _  <- qa.zipPar(qb).run
        ac <- a.calls.get
        bc <- b.calls.get
      } yield assertTrue(ac.size == 1, bc.size == 1)
    }
  )
}
