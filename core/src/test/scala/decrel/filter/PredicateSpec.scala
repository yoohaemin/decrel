/*
 * Copyright (c) 2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter

import decrel.*
import zio.test.*

object PredicateSpec extends ZIOSpecDefault {
  import Predicate.*

  case object Edge            extends Relation.Single[Int, Int]
  case object OptionalPayload extends Relation.Single[Unit, Option[Int]]

  override def spec: Spec[TestEnvironment, Any] = suite("typed predicates")(
    test("input and output roots remain distinct for the same Scala type") {
      val predicate = Predicate.build[Int, Int]((in, out) => out <= in)
      assertTrue(
        predicate.toAst == Right(Compare(Output, Comparison.LessThanOrEqual, Input, Scalar.IntType))
      )
    },
    test("literal and Boolean operators produce a closed tree") {
      val predicate = Predicate.build[Int, Int] { (in, out) =>
        ((out >= 5) && (in !== 0)) || !(out === 9)
      }
      assertTrue(
        predicate.toAst == Right(
          Or(
            And(
              Compare(
                Output,
                Comparison.GreaterThanOrEqual,
                Literal(5, Scalar.IntType),
                Scalar.IntType
              ),
              Compare(Input, Comparison.NotEqual, Literal(0, Scalar.IntType), Scalar.IntType)
            ),
            Not(Compare(Output, Comparison.Equal, Literal(9, Scalar.IntType), Scalar.IntType))
          )
        )
      )
    },
    test("Option operations retain explicit two-valued scopes") {
      val predicate = Predicate.build[Unit, Option[Int]] { (_, out) =>
        out.isEmpty || !out.exists(_ > 5)
      }
      assertTrue(
        predicate.toAst == Right(
          Or(
            Not(IsDefined(Output)),
            Not(
              Exists(
                Output,
                Compare(
                  OptionValue(Output),
                  Comparison.GreaterThan,
                  Literal(5, Scalar.IntType),
                  Scalar.IntType
                )
              )
            )
          )
        )
      )
    },
    test("nested Option scopes can reference an outer payload") {
      val predicate = Predicate.build[Int, Option[Option[Int]]] { (in, out) =>
        out.exists(outer => outer.exists(value => value > in))
      }
      assertTrue(predicate.toAst.isRight)
    },
    test("an Option payload cannot escape its exists scope") {
      var escaped = Option.empty[Expr[Unit, Option[Int], Int]]
      Predicate.build[Unit, Option[Int]] { (_, out) =>
        out.exists { value =>
          escaped = Some(value)
          value > 0
        }
      }
      val invalid  = escaped.get > 0
      var compiled = false
      val compiler = new FilterCompiler[Unit] {
        override protected def compileAst(tree: Test): Either[FilterError, Unit] = {
          compiled = true
          Right(())
        }
      }
      assertTrue(
        compiler.compile(invalid) == Left(
          FilterError(Vector("left"), "Option value escaped its exists scope")
        ),
        scala.util.Try(OptionalPayload.filter(invalid)).isFailure,
        scala.util.Try(Predicate.build[Unit, Option[Int]]((_, _) => invalid)).isFailure,
        !compiled
      )
    },
    test("independently built predicates and filtered edges have structural equality") {
      val first     = Edge.filter((_, out) => out > 1)
      val second    = Edge.filter((_, out) => out > 1)
      val different = Edge.filter((_, out) => out > 2)
      val cache     = decrel.reify.Cache.empty.add(first, 0, Some(2)).add(second, 0, Some(3))
      assertTrue(
        first == second,
        first.hashCode == second.hashCode,
        first != different,
        cache.entries.size == 1
      )
    },
    test("the builder executes once and is not part of the predicate value") {
      var builds   = 0
      val relation = Edge.filter { (_, out) =>
        builds += 1
        out === 1
      }
      relation.filter.toAst
      relation.filter.toAst
      assertTrue(builds == 1)
    },
    test("compiler failures retain their node path and reason") {
      val error    = FilterError(Vector("left"), "Ordered comparisons are unsupported")
      val compiler = new FilterCompiler[String] {
        override protected def compileAst(tree: Test): Either[FilterError, String] = Left(error)
      }
      assertTrue(compiler.compile(Predicate.build[Int, Int]((_, out) => out > 1)) == Left(error))
    },
    test("scalar literals retain their type and reject null") {
      val literals = List(
        Expr.literal[Unit, Unit, Boolean](true).node,
        Expr.literal[Unit, Unit, Byte](1.toByte).node,
        Expr.literal[Unit, Unit, Short](1.toShort).node,
        Expr.literal[Unit, Unit, Int](1).node,
        Expr.literal[Unit, Unit, Long](1L).node,
        Expr.literal[Unit, Unit, Float](1.0f).node,
        Expr.literal[Unit, Unit, Double](1.0d).node,
        Expr.literal[Unit, Unit, Char]('a').node,
        Expr.literal[Unit, Unit, String]("a").node,
        Expr.literal[Unit, Unit, BigInt](BigInt(1)).node,
        Expr.literal[Unit, Unit, BigDecimal](BigDecimal(1)).node
      )
      assertTrue(
        literals.distinct.size == 11,
        scala.util.Try(Expr.literal[Unit, Unit, String](null)).isFailure
      )
    },
    test("comparisons require matching scalar types") {
      typeCheck("""import decrel.filter._
        Predicate.build[Int, Int]((_, out) => out === "wrong")
      """).map(result => assertTrue(result.isLeft))
    },
    test("floating-point literal identity is stable for NaNs and preserves signed zero") {
      val first        = Predicate.build[Unit, Double]((_, out) => out !== Double.NaN)
      val second       = Predicate.build[Unit, Double]((_, out) => out !== Double.NaN)
      val positiveZero = Predicate.build[Unit, Double]((_, out) => out === 0.0d)
      val negativeZero = Predicate.build[Unit, Double]((_, out) => out === -0.0d)
      assertTrue(first == second, first.hashCode == second.hashCode, positiveZero != negativeZero)
    },
    test("functions cannot be used as filter literals") {
      typeCheck("""import decrel.filter._
        Expr.literal[Unit, Unit, Int => Int]((n: Int) => n)
      """).map(result => assertTrue(result.isLeft))
    },
    test("proofs cannot advertise arbitrary filter types") {
      typeCheck("""import decrel._
        decrel.reify.either.Proof.widenRelationProof[Relation.Single[Int, Int], Int, Nothing, Int, String](null)
      """).map(result => assertTrue(result.isLeft))
    },
    test("expression comparisons do not widen different numeric types") {
      typeCheck("""import decrel.filter._
        Predicate.build[Long, Int]((in, out) => out < in)
      """).map(result => assertTrue(result.isLeft))
    },
    test("Boolean fields cannot be ordered") {
      typeCheck("""import decrel.filter._
        Predicate.build[Unit, Boolean]((_, out) => out > false)
      """).map(result => assertTrue(result.isLeft))
    },
    test("a filter builder must return a predicate") {
      typeCheck("""import decrel._
        case object Edge extends Relation.Single[Int, Int]
        Edge.filter((in, out) => true)
      """).map(result => assertTrue(result.isLeft))
    },
    test("arbitrary filter values are rejected") {
      typeCheck("""import decrel._
        case object Edge extends Relation.Single[Int, Int]
        Edge.filter("backend-specific")
      """).map(result => assertTrue(result.isLeft))
    },
    test("custom implementations cannot be filtered") {
      typeCheck("""import decrel._
        import decrel.filter._
        case object Edge extends Relation.Single[Int, Int]
        Edge.customImpl.filter(Predicate.always[Int, Int])
      """).map(result => assertTrue(result.isLeft))
    },
    test("composed expressions cannot be filtered") {
      typeCheck("""import decrel._
        import decrel.filter._
        case object Edge extends Relation.Single[Int, Int]
        (Edge >>: Edge).filter(Predicate.always[Int, Int])
      """).map(result => assertTrue(result.isLeft))
    },
    test("already filtered expressions cannot be filtered again") {
      typeCheck("""import decrel._
        import decrel.filter._
        case object Edge extends Relation.Single[Int, Int]
        Edge.filter(Predicate.always[Int, Int]).filter(Predicate.never[Int, Int])
      """).map(result => assertTrue(result.isLeft))
    }
  )
}
