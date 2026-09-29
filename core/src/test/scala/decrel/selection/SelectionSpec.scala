/* SPDX-License-Identifier: MPL-2.0 */
package decrel.selection

import decrel.*
import decrel.filter.*
import decrel.filter.OrderBy.*
import decrel.selection.syntax.*
import zio.test.*

object SelectionSpec extends ZIOSpecDefault {
  case object Numbers  extends Relation.Many[Int, List, Int]
  case object Maybe    extends Relation.Optional[Int, Int]
  case object Required extends Relation.Single[Int, Int]

  private val reference = new ReferenceSelection((_, field) =>
    Left(FilterError(Vector(field), "No fields in the scalar test backend"))
  )

  override def spec: Spec[TestEnvironment, Any] = suite("selection language")(
    test("filter -> order -> cardinality, not a different permutation") {
      // Original head 9 fails. Filtered head 3 is not the greatest. Only the
      // requested stage order produces 8.
      val selected = Numbers
        .filter((in, out) => out <= in)
        .orderBy((_, out) => out.desc)
        .headOption
      val result = reference.compile(selected.plan).flatMap(_.apply(8, Vector(9, 3, 8)))
      assertTrue(
        result == Right(Vector(8)),
        selected.plan.toAst.map(_.stages.map(_.getClass.getSimpleName)) ==
          Right(Vector("FilterStage", "OrderStage", "CardinalityStage"))
      )
    },
    test("ordering without cardinality keeps all candidates") {
      val selected = Numbers.orderBy((_, out) => out.asc)
      assertTrue(
        reference.compile(selected.plan).flatMap(_.apply(0, Vector(9, 3, 8))) ==
          Right(Vector(3, 8, 9))
      )
    },
    test("head selection is per input, not per batch") {
      val selected = Numbers
        .filter((in, out) => out <= in)
        .orderBy((_, out) => out.desc)
        .headOption
      val result = reference.compile(selected.plan).map { program =>
        List(5, 10).map(in => program(in, Vector(9, 3, 8)))
      }
      assertTrue(result == Right(List(Right(Vector(3)), Right(Vector(9)))))
    },
    test("an unordered head promises membership, not a reproducible winner") {
      val selected   = Numbers.headOption
      val candidates = Set(2, 4, 7).toVector
      val result     = reference.compile(selected.plan).flatMap(_.apply(0, candidates))
      assertTrue(result.exists(rows => rows.size == 1 && candidates.contains(rows.head)))
    },
    test("direction and cardinality participate in structural identity") {
      val a = Numbers.orderBy((_, out) => out.asc).headOption
      val b = Numbers.orderBy((_, out) => out.asc).headOption
      val c = Numbers.orderBy((_, out) => out.desc).headOption
      val d = Numbers.orderBy((_, out) => out.asc).head
      assertTrue(a == b, a.hashCode == b.hashCode, a.plan != c.plan, a.plan != d.plan)
    },
    test("an unsupported scalar is rejected, not silently sorted") {
      case object Doubles extends Relation.Many[Unit, List, Double]
      val selected = Doubles.orderBy((_, out) => out.asc)
      assertTrue(reference.compile(selected.plan).isLeft)
    },
    test("no filter after orderBy") {
      typeCheck("""import decrel._
        import decrel.filter._
        import decrel.filter.OrderBy._
        import decrel.selection.syntax._
        import decrel.selection.SelectionSpec._
        Numbers.orderBy((_, out) => out.asc).filter(Predicate.always[Int, Int])
      """).map(result => assertTrue(result.isLeft))
    },
    test("no orderBy after headOption") {
      typeCheck("""import decrel._
        import decrel.filter._
        import decrel.filter.OrderBy._
        import decrel.selection.syntax._
        import decrel.selection.SelectionSpec._
        Numbers.headOption.orderBy(OrderBy.build[Int, Int]((_, out) => out.asc))
      """).map(result => assertTrue(result.isLeft))
    },
    test("headOption is not offered on Optional") {
      typeCheck("""import decrel._
        import decrel.selection.syntax._
        import decrel.selection.SelectionSpec._
        Maybe.headOption
      """).map(result => assertTrue(result.isLeft))
    },
    test("head is not offered on an unfiltered Single") {
      typeCheck("""import decrel._
        import decrel.selection.syntax._
        import decrel.selection.SelectionSpec._
        Required.head
      """).map(result => assertTrue(result.isLeft))
    },
    test("filtered Single becomes Optional and can require a head") {
      val result: Relation[Int, Int] = Required.filter(Predicate.always[Int, Int]).head
      assertTrue(result != null)
    }
  )
}
