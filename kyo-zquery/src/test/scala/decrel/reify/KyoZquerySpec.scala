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

  class Interpreter extends kyoZquery[String] {
    var calls                                                   = List.empty[List[Int]]
    implicit val lookup: Proof.Single[Lookup.type, Int, String] =
      implementSingleDatasource(Lookup) { ids =>
        Sync.defer {
          calls = calls :+ ids.toList
          ids.map(id => id -> s"v$id").toMap
        }
      }
  }

  def spec = suite("Kyo callbacks in ZQuery")(
    test("one registration supports handled Kyo, exposed Batch, and ZQuery") {
      val module = new Interpreter
      import module.*
      for {
        handled <- ZIOs.run(Lookup.startingFromMany(Vector(2, 1, 2)))
        batched <- ZIOs.run(_root_.kyo.Batch.run(Lookup.toBatchMany(Vector(2, 1, 2))))
        queried <- Lookup.toZQueryMany(Vector(2, 1, 2)).run
      } yield assertTrue(handled == queried, batched.toVector == Vector(queried), calls.size == 3)
    },
    test("separately constructed queries share a batch when combined") {
      val module = new Interpreter
      import module.KyoZqueryRelationOps
      import module.*
      val left   = Lookup.toZQueryMany(Vector(2, 1, 2))
      val right  = Lookup.toZQueryMany(List(1, 3))
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
      import module.KyoZqueryRelationOps
      import module.*
      var nextCalls                                            = List.empty[List[String]]
      implicit val next: Proof.Single[Next.type, String, Long] =
        implementSingleDatasource(Next) { ids =>
          Sync.defer {
            nextCalls = nextCalls :+ ids.toList
            ids.map(id => id -> id.drop(1).toLong).toMap
          }
        }
      val path = Lookup >>: Next
      (path & path).toZQueryMany(Vector(2, 1, 2)).run.map { result =>
        assertTrue(
          result == Vector((2L, 2L), (1L, 1L), (2L, 2L)),
          calls.size == 1,
          nextCalls.size == 1,
          nextCalls.head.toSet == Set("v1", "v2")
        )
      }
    },
    test("optional and many callbacks preserve absent positions and group boundaries") {
      val module = new Interpreter
      import module.KyoZqueryRelationOps
      implicit val optional: module.Proof.Optional[Optional.type, Int, String] =
        module.implementOptionalDatasource[Optional.type, Int, String](Optional) { ids =>
          ids.map(id => id -> Option.when(id > 0)(s"v$id")).toMap
        }
      implicit val many: module.Proof.Many[Many.type, Int, Vector, String] =
        module.implementManyDatasource[Many.type, Int, Vector, String](Many) { ids =>
          ids.map(id => id -> Vector.fill(id)(s"v$id")).toMap
        }
      for {
        a <- Optional.toZQueryMany(Vector(1, 0, 1)).run
        b <- Many.toZQueryMany(List(2, 0, 1)).run
      } yield assertTrue(
        a == Vector(Some("v1"), None, Some("v1")),
        b == List(Vector("v2", "v2"), Vector.empty, Vector("v1"))
      )
    },
    test("custom relation callbacks support asynchronous Kyo effects") {
      val module = new Interpreter
      import module.KyoZqueryRelationOps
      var calls                                                                        = 0
      def execute[Tree: izumi.reflect.Tag](relation: Relation.Custom[Tree, Int, Long]) = {
        implicit val proof: module.Proof[Relation.Custom[Tree, Int, Long], Int, Long] =
          module.implementCustomDatasource(relation) { ids =>
            ZIOs.get(zio.ZIO.yieldNow *> zio.ZIO.succeed {
              calls += 1
              ids.map(id => id -> id.toLong).toMap
            })
          }
        relation.toZQueryMany(Vector(2, 1, 2)).run
      }
      execute((Lookup >>: Next).customImpl).map(result =>
        assertTrue(result == Vector(2L, 1L, 2L), calls == 1, module.calls.isEmpty)
      )
    },
    test("typed Kyo failures become ZQuery failures") {
      val module = new Interpreter
      import module.KyoZqueryRelationOps
      implicit val failing: module.Proof.Single[Lookup.type, Int, String] =
        module.implementSingleDatasource[Lookup.type, Int, String](Lookup) { _ =>
          Abort.fail("missing")
        }
      Lookup.toZQuery(1).run.either.map(result => assertTrue(result == Left("missing")))
    },
    test("empty inputs do not fetch and rerunning a query fetches again") {
      val module = new Interpreter
      import module.KyoZqueryRelationOps
      import module.lookup
      val query = Lookup.toZQuery(1)
      for {
        empty <- Lookup.toZQueryMany(List.empty[Int]).run
        before = module.calls
        first  <- query.run
        second <- query.run
      } yield assertTrue(empty.isEmpty, before.isEmpty, first == second, module.calls.size == 2)
    }
  )
}
