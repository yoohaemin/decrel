/*
 * Copyright (c) 2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.ziotest

import decrel.*
import decrel.filter.Predicate
import decrel.ziotest.gen.*
import zio.test.*

object TypedFilterGenSpec extends ZIOSpecDefault {
  case object Number      extends Relation.Single[Int, Int]
  case object TextNumber  extends Relation.Single[String, Int]
  case object Numbers     extends Relation.Many[Int, List, Int]
  case object MaybeNumber extends Relation.Optional[Int, Int]

  override def spec: Spec[TestEnvironment, Any] = suite("typed generator filters")(
    test("function contramap preserves unfiltered generator access") {
      val base
        : Proof.Single[Number.type & Relation.Single[Int, Int], Int, Int, Predicate[Int, Int]] =
        Gen.filteredRelationSingle[Number.type, Int, Int](Number) { (in, predicate) =>
          Gen.const(if (predicate.isEmpty) Some(in) else None)
        }
      implicit val derived
        : Proof.Single[TextNumber.type & Relation.Single[String, Int], String, Int, Nothing] =
        base.contramap(TextNumber)((in: String) => in.length)
      check(Gen.const("abc").expand(TextNumber))(value => assertTrue(value == 3))
    },
    test("optional and many generators receive predicates for their elements") {
      val predicate = Predicate.build[Int, Int]((in, out) => out <= in)
      implicit val optional: Proof.Optional[
        MaybeNumber.type & Relation.Optional[Int, Int],
        Int,
        Int,
        Predicate[Int, Int]
      ] = Gen.filteredRelationOptional[MaybeNumber.type, Int, Int](MaybeNumber) { (in, p) =>
        Gen.const(if (p.contains(predicate)) Some(in) else None)
      }
      implicit val many
        : Proof.Many[Numbers.type & Relation.Many[Int, List, Int], Int, List, Int, Predicate[
          Int,
          Int
        ]] = Gen.filteredRelationMany[Numbers.type, Int, Int, List](Numbers) { (in, p) =>
        Gen.const(if (p.contains(predicate)) List(in, in - 1) else Nil)
      }
      val relation = MaybeNumber.filter(predicate) & Numbers.filter(predicate)
      check(Gen.const(3).expand(relation)) { result =>
        assertTrue(result == (Some(3), List(3, 2)))
      }
    },
    test("function contramap cannot derive filtered generator access") {
      typeCheck("""import decrel._
        import decrel.filter._
        import decrel.ziotest.gen._
        import decrel.ziotest.TypedFilterGenSpec._
        import zio.test.Gen
        val base: Proof.Single[Number.type & Relation.Single[Int, Int], Int, Int, Predicate[Int, Int]] = Gen.filteredRelationSingle[Number.type, Int, Int](Number) { (in, _) => Gen.const(Option(in)) }
        implicit val derived: Proof.Single[TextNumber.type & Relation.Single[String, Int], String, Int, Nothing] = base.contramap(TextNumber)((in: String) => in.length)
        Gen.const("abc").expand(TextNumber.filter(Predicate.always[String, Int]))
      """).map(result => assertTrue(result.isLeft))
    }
  )
}
