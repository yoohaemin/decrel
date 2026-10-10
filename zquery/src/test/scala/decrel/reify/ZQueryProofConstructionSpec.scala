/*
 * Copyright (c) Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import scala.collection.IterableOps
import scala.language.implicitConversions
import zio.*
import zio.query.ZQuery
import zio.test.*

object ZQueryProofConstructionSpec extends ZIOSpecDefault {
  final case class Offset(amount: Int) extends Relation.Single[Int, Int]

  class Module extends zquery[Any] {
    val constructed = scala.collection.mutable.ArrayBuffer.empty[Int]

    implicit def offsetProof(value: Offset): Proof.Single[Offset, Int, Nothing, Int] = {
      constructed += value.amount
      new Proof.Single[Offset, Int, Nothing, Int] {
        override val reify: ReifiedRelation[Int, Nothing, Int] =
          new ReifiedRelation.Custom[Int, Nothing, Int] {
            override def apply(in: Int): ZQuery[Any, Nothing, Int] =
              ZQuery.succeed(in + value.amount)

            override def applyMultiple[CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
              in: CC[Int]
            ): ZQuery[Any, Nothing, CC[Int]] = ZQuery.succeed(in.map(_ + value.amount))
          }
      }
    }
  }

  override def spec: Spec[Environment, Any] = suite("ZQuery proof construction")(
    test("cached effect binds its proof before execution and reuses it on subsequent runs") {
      val module = new Module
      import module.*
      val effect    = Offset(7).toZIO(2, Cache.empty)
      val beforeRun = constructed.toVector
      effect.zip(effect).map { case (first, second) =>
        assertTrue(
          beforeRun == Vector(7),
          first == 9,
          second == 9,
          constructed.toVector == beforeRun
        )
      }
    }
  )
}
