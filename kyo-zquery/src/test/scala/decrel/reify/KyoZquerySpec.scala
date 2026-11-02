/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import _root_.kyo.{ Abort, Sync, ZIOs }
import zio.test.*

object KyoZquerySpec extends ZIOSpecDefault {
  object Lookup   extends Relation.Single[Int, String]
  object Optional extends Relation.Optional[Int, String]
  object Many     extends Relation.Many[Int, Vector, String]
  object Next     extends Relation.Single[String, Long]

  class Interpreter extends kyoZquery {
    var calls                                                           = List.empty[List[Int]]
    implicit val lookup: Proof.Single[Lookup.type, Int, String, String] =
      implementSingleKyoDatasource(Lookup) { ids =>
        Sync.defer {
          calls = calls :+ ids.toList
          ids.map(id => id -> s"v$id")
        }
      }
  }

  def spec = suite("Kyo callbacks in ZQuery")(
    test("separately constructed queries share a batch when combined") {
      val module = new Interpreter
      val left   = module.lookup.reify.applyMultiple(Vector(2, 1, 2))
      val right  = module.lookup.reify.applyMultiple(List(1, 3))
      val before = module.calls
      left.zipWithBatched(right)((_, _)).run.map { result =>
        assertTrue(
          before.isEmpty,
          result == ((Vector("v2", "v1", "v2"), List("v1", "v3"))),
          module.calls.size == 1,
          module.calls.head.toSet == Set(1, 2, 3)
        )
      }
    },
    test("relation composition batches independent branches and dependent stages") {
      val module = new Interpreter
      import module.*
      var nextCalls                                                    = List.empty[List[String]]
      implicit val next: Proof.Single[Next.type, String, String, Long] =
        implementSingleKyoDatasource(Next) { ids =>
          Sync.defer {
            nextCalls = nextCalls :+ ids.toList
            ids.map(id => id -> id.drop(1).toLong)
          }
        }
      val path = Lookup >>: Next
      (path & path).toQueryMany(Vector(2, 1, 2)).run.map { result =>
        assertTrue(
          result == Vector((2L, 2L), (1L, 1L), (2L, 2L)),
          calls.size == 1,
          nextCalls.size == 1,
          nextCalls.head.toSet == Set("v1", "v2")
        )
      }
    },
    test("optional and many callbacks preserve absent positions and group boundaries") {
      val module   = new Interpreter
      val optional =
        module.implementOptionalKyoDatasource[Optional.type, Int, Nothing, String](Optional) { ids =>
          ids.map(id => id -> Option.when(id > 0)(s"v$id"))
        }
      val many = module.implementManyKyoDatasource[Many.type, Int, Nothing, String, Vector](Many) {
        ids => ids.map(id => id -> Vector.fill(id)(s"v$id"))
      }
      for {
        a <- optional.reify.applyMultiple(Vector(1, 0, 1)).run
        b <- many.reify.applyMultiple(List(2, 0, 1)).run
      } yield assertTrue(
        a == Vector(Some("v1"), None, Some("v1")),
        b == List(Vector("v2", "v2"), Vector.empty, Vector("v1"))
      )
    },
    test("custom relation callbacks support asynchronous Kyo effects") {
      val module   = new Interpreter
      val relation = (Lookup >>: Next).customImpl
      var calls    = 0
      val proof    = module.implementCustomKyoDatasource(relation) { ids =>
        ZIOs.get(zio.ZIO.yieldNow *> zio.ZIO.succeed {
          calls += 1
          ids.map(id => id -> id.toLong)
        })
      }
      proof.reify.applyMultiple(Vector(2, 1, 2)).run.map { result =>
        assertTrue(result == Vector(2L, 1L, 2L), calls == 1, module.calls.isEmpty)
      }
    },
    test("typed Kyo failures become ZQuery failures") {
      val module  = new Interpreter
      val failing = module.implementSingleKyoDatasource[Lookup.type, Int, String, String](Lookup) {
        _ => Abort.fail("missing")
      }
      failing.reify.apply(1).run.either.map(result => assertTrue(result == Left("missing")))
    },
    test("empty inputs do not fetch and rerunning a query fetches again") {
      val module = new Interpreter
      val query  = module.lookup.reify.apply(1)
      for {
        empty <- module.lookup.reify.applyMultiple(List.empty[Int]).run
        before = module.calls
        first  <- query.run
        second <- query.run
      } yield assertTrue(empty.isEmpty, before.isEmpty, first == second, module.calls.size == 2)
    }
  )
}
