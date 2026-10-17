/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import _root_.kyo.{ Abort, * }
import zio.test.*

object KyoBatchSpec extends ZIOSpecDefault {
  case object Lookup   extends Relation.Single[Int, String]
  case object Group    extends Relation.Many[List[Int], List, String]
  case object Maybe    extends Relation.Optional[Option[Int], String]
  case object Next     extends Relation.Single[String, Long]
  case object Other    extends Relation.Single[Int, String]
  case object Children extends Relation.Many[Int, Vector, Int]
  case object Present  extends Relation.Optional[Int, Int]

  val customPath = (Lookup >>: Next).customImpl

  class Interpreter extends kyoBatch[Any] {
    var calls                                                   = Vector.empty[List[Int]]
    var nextCalls                                               = Vector.empty[List[String]]
    var otherCalls                                              = Vector.empty[List[Int]]
    implicit val lookup: Proof.Single[Lookup.type, Int, String] =
      implementSingleDatasource(Lookup) { ids =>
        calls = calls :+ ids.toList
        ids.map(i => i -> s"v$i").toMap
      }
    implicit val other: Proof.Single[Other.type, Int, String] =
      implementSingleDatasource(Other) { ids =>
        otherCalls = otherCalls :+ ids.toList
        ids.map(i => i -> s"other$i").toMap
      }
    implicit val next: Proof.Single[Next.type, String, Long] =
      implementSingleDatasource(Next) { ids =>
        nextCalls = nextCalls :+ ids.toList
        ids.map(i => i -> i.drop(1).toLong).toMap
      }
    implicit val group: Proof.Many[Group.type, List[Int], List, String] =
      contramapManyProof(lookup, Group, (ids: List[Int]) => ids)
    implicit val maybe: Proof.Optional[Maybe.type, Option[Int], String] =
      contramapOptionalProof(lookup, Maybe, (id: Option[Int]) => id)
    implicit val children: Proof.Many[Children.type, Int, Vector, Int] =
      implementManyDatasource(Children)(ids => ids.map(i => i -> Vector(i, i + 1)).toMap)
    implicit val present: Proof.Optional[Present.type, Int, Int] =
      implementOptionalDatasource(Present)(ids => ids.map(i => i -> Option.when(i > 0)(i)).toMap)
  }

  // An asynchronous boundary lets the Scala.js test runner observe completion events.
  private def asyncTest(label: String)(assertion: => TestResult) =
    test(label)(zio.ZIO.fromFuture(ec => scala.concurrent.Future(assertion)(using ec)))

  def spec = suite("Kyo composed batching")(
    asyncTest("bulk deduplicates and preserves positions and collection shape") {
      val module = new Interpreter
      import module.*
      val result = Lookup.startingFromMany(Vector(2, 1, 2)).eval
      assertTrue(result == Vector("v2", "v1", "v2"), calls == Vector(List(2, 1)))
    },
    asyncTest("zipped branches share one datasource batch") {
      val module = new Interpreter
      import module.*
      val result = (Lookup & Lookup).startingFromMany(List(1, 2)).eval
      assertTrue(result == List(("v1", "v1"), ("v2", "v2")), calls == Vector(List(1, 2)))
    },
    asyncTest("derived many groups batch together and retain empty groups and duplicates") {
      val module = new Interpreter
      import module.*
      val result = Group.startingFromMany(Vector(List(1, 2), Nil, List(2, 3, 2))).eval
      assertTrue(
        result == Vector(List("v1", "v2"), Nil, List("v2", "v3", "v2")),
        calls == Vector(List(1, 2, 3))
      )
    },
    asyncTest("derived optional inputs share batches and preserve absent positions") {
      val module = new Interpreter
      import module.*
      val result = Maybe.startingFromMany(List(None, Some(2), Some(2), None, Some(1))).eval
      assertTrue(
        result == List(None, Some("v2"), Some("v2"), None, Some("v1")),
        calls == Vector(List(2, 1))
      )
    },
    asyncTest("mixed datasource results retain their branch positions") {
      val module = new Interpreter
      import module.*
      val result = (Lookup & Other & Lookup).startingFromMany(List(2, 1)).eval
      assertTrue(
        result == List(("v2", "other2", "v2"), ("v1", "other1", "v1")),
        calls == Vector(List(2, 1)),
        otherCalls == Vector(List(2, 1))
      )
    },
    asyncTest("dependent stages batch across branches after their inputs become available") {
      val module = new Interpreter
      import module.*
      val path   = Lookup >>: Next
      val result = (path & path).startingFromMany(List(2, 1, 2)).eval
      assertTrue(
        result == List((2L, 2L), (1L, 1L), (2L, 2L)),
        calls == Vector(List(2, 1)),
        nextCalls == Vector(List("v2", "v1"))
      )
    },
    asyncTest("declared many and optional relations compose with bulk requests") {
      val module = new Interpreter
      import module.*
      val result = ((Children >>: Lookup) & (Present >>: Lookup)).startingFromMany(List(0, 1)).eval
      assertTrue(
        result == List((Vector("v0", "v1"), None), (Vector("v1", "v2"), Some("v1"))),
        calls == Vector(List(0, 1, 2))
      )
    },
    asyncTest("empty bulk and absent optional inputs make no requests") {
      val module = new Interpreter
      import module.*
      val empty  = Lookup.startingFromMany(List.empty[Int]).eval
      val absent = Maybe.startingFrom(None).eval
      assertTrue(empty.isEmpty, absent.isEmpty, calls.isEmpty)
    },
    asyncTest("a compiled query can be rerun without retaining results or mutable builders") {
      val module = new Interpreter
      import module.*
      val query  = Group.reify.applyMultiple(List(List(1, 2), List(2, 3)))
      val before = calls
      val first  = query.run.eval
      val second = query.run.eval
      assertTrue(
        before.isEmpty,
        first == second,
        first == List(List("v1", "v2"), List("v2", "v3")),
        calls == Vector(List(1, 2, 3), List(1, 2, 3))
      )
    },
    asyncTest("custom datasources participate in composed batches") {
      val module = new Interpreter
      import module.*
      var batches = Vector.empty[List[Int]]
      def execute[Tree: izumi.reflect.Tag](relation: Relation.Custom[Tree, Int, Long]) = {
        implicit val proof: Proof[Relation.Custom[Tree, Int, Long], Int, Long] =
          implementCustomDatasource(relation) { ids =>
            batches = batches :+ ids.toList
            ids.map(i => i -> (i + 10L)).toMap
          }
        (relation & relation).startingFromMany(List(2, 1)).eval
      }
      val result = execute(customPath)
      assertTrue(
        result == List((12L, 12L), (11L, 11L)),
        batches == Vector(List(2, 1)),
        calls.isEmpty
      )
    },
    asyncTest("datasource effects preserve typed failures") {
      class FailingInterpreter extends kyoBatch[Abort[String]] {
        implicit val lookup: Proof.Single[Lookup.type, Int, String] =
          implementSingleDatasource(Lookup)(_ => Abort.fail("missing"))
      }
      val module = new FailingInterpreter
      import module.*
      val result = Abort.run[String]((Lookup & Lookup).startingFrom(1)).eval
      assertTrue(result.isFailure)
    },
    asyncTest("pure branches retain their inputs alongside pending requests") {
      val module = new Interpreter
      import module.*
      val result = (Relation.Self[Int] & Lookup).startingFromMany(Vector(2, 1, 2)).eval
      assertTrue(result == Vector((2, "v2"), (1, "v1"), (2, "v2")), calls == Vector(List(2, 1)))
    },
    asyncTest("lift delays custom effect construction and reevaluates on each run") {
      var executions = 0
      val query      = KyoBatchQuery
        .lift[Any, Int] {
          executions += 1
          executions
        }
        .map(_ * 10)
      val before = executions
      val first  = query.run.eval
      val second = query.run.eval
      assertTrue(before == 0, first == 10, second == 20, executions == 2)
    },
    asyncTest("large bulk traversal uses balanced bookkeeping") {
      val module = new Interpreter
      import module.*
      val ids    = (1 to 4096).toVector
      val result = Lookup.startingFromMany(ids).eval
      assertTrue(result == ids.map(i => s"v$i"), calls.size == 1)
    }
  )
}
