/*
 * Copyright (c) Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.{ Relation, RelationComposeSyntax }
import kyo.*
import scala.collection.IterableOps
import scala.language.implicitConversions
import zio.test.*

object KyoProofConstructionSpec extends ZIOSpecDefault {
  final case class Offset(amount: Int) extends Relation.Single[Int, Int]

  class Module extends kyoBatch[Any] {
    val constructed = scala.collection.mutable.ArrayBuffer.empty[Int]

    implicit def offsetProof(value: Offset): Proof.Single[Offset, Int, Int] = {
      constructed += value.amount
      new Proof.Single[Offset, Int, Int] {
        override val reify: ReifiedRelation[Int, Int] =
          new ReifiedRelation.Custom[Int, Int] {
            override def apply(in: Int): Int < Any = in + value.amount

            override def applyMultiple[CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
              in: CC[Int]
            ): CC[Int] < Any = in.map(_ + value.amount)
          }
      }
    }
  }

  override def spec: Spec[Environment, Any] = suite("Kyo proof construction")(
    test("composed relation syntax uses the actual child values for single and batch inputs") {
      val module = new Module
      import module.*
      val first  = Offset(1) >>: Offset(10)
      val second = Offset(2) >>: Offset(20)
      assertTrue(
        first.startingFrom(2).eval == 13,
        first.startingFromMany(Vector(2, 3)).eval == Vector(13, 14),
        second.startingFrom(2).eval == 24,
        second.startingFromMany(Vector(2, 3)).eval == Vector(24, 25)
      )
    },
    test("syntax binds composed child proofs during construction and reuses them") {
      val module = new Module
      import module.*
      val relation = Offset(1) >>: (Offset(10) >>: Offset(100))
      val ops      = new KyoRelationOps(relation)
      val before   = constructed.toVector
      assertTrue(
        before == Vector(1, 10, 100),
        ops.startingFrom(2).eval == 113,
        ops.startingFromMany(Vector(2, 3)).eval == Vector(113, 114),
        ops.startingFrom(4).eval == 115,
        constructed.toVector == before
      )
    }
  )
}
