/*
 * Copyright (c) 2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import cats.effect.{ Concurrent, IO, Ref }
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import _root_.fetch.fetchM
import decrel.*
import decrel.filter.Predicate
import zio.ZIO
import zio.test.*

object FetchFilterSpec extends ZIOSpecDefault {
  case object Number    extends Relation.Single[Int, Int]
  object implementation extends decrel.reify.fetch[IO] {
    override protected implicit val CF: Concurrent[IO] = IO.asyncForIO
  }

  override def spec: Spec[TestEnvironment, Any] = suite("Fetch typed filters")(
    test("equal filters deduplicate and different filters isolate the same input") {
      import implementation.*
      val positive = Predicate.build[Int, Int]((_, out) => out > 0)
      val program  = for {
        calls  <- Ref.of[IO, List[Option[Predicate[Int, Int]]]](Nil)
        result <- {
          implicit val proof
            : Proof.Single[Number.type & Relation.Single[Int, Int], Int, Int, Predicate[Int, Int]] =
            implementFilteredSingleDatasource[Number.type, Int, Int](Number) { (inputs, predicate) =>
              calls.update(_ :+ predicate) *> (predicate match {
                case Some(p) if p == positive  => IO.pure(inputs.map(_ -> Some(1)))
                case Some(p) if p == !positive => IO.pure(inputs.map(_ -> None))
                case _ => IO.raiseError(new IllegalArgumentException("Unexpected predicate"))
              })
            }
          val first = Number.filter((_, out) => out > 0)
          val equal = Number.filter((_, out) => out > 0)
          _root_.fetch.Fetch.run(
            (first.toFetch(1), equal.toFetch(1), Number.filter(!positive).toFetch(1)).tupled
          )
        }
        recorded <- calls.get
      } yield assertTrue(
        result == (Some(1), Some(1), None),
        recorded.size == 2,
        recorded.toSet == Set[Option[Predicate[Int, Int]]](Some(positive), Some(!positive))
      )
      ZIO.fromFuture(_ => program.unsafeToFuture())
    },
    test("equal filtered relations consume preloaded optional rows") {
      import implementation.*
      val predicate = Predicate.build[Int, Int]((_, out) => out > 0)
      val cache     = Cache.empty.add(Number.filter(predicate), 1, Some(42))
      val program   = for {
        calls  <- Ref.of[IO, Int](0)
        result <- {
          implicit val proof
            : Proof.Single[Number.type & Relation.Single[Int, Int], Int, Int, Predicate[Int, Int]] =
            implementFilteredSingleDatasource[Number.type, Int, Int](Number) { (inputs, _) =>
              calls.update(_ + 1).as(inputs.map(id => id -> Option(id)))
            }
          Number.filter((_, out) => out > 0).startingFrom(1, cache)
        }
        count <- calls.get
      } yield assertTrue(result == Some(42), count == 0)
      ZIO.fromFuture(_ => program.unsafeToFuture())
    },
    test("unfiltered filter-aware singles honor preloaded row values") {
      import implementation.*
      val program = for {
        calls  <- Ref.of[IO, List[List[Int]]](Nil)
        result <- {
          implicit val proof
            : Proof.Single[Number.type & Relation.Single[Int, Int], Int, Int, Predicate[Int, Int]] =
            implementFilteredSingleDatasource[Number.type, Int, Int](Number) { (inputs, _) =>
              calls.update(_ :+ inputs).as(inputs.map(id => id -> Some(id)))
            }
          Number.startingFrom(List(7, 8), Cache.empty.add(Number, 7, 42))
        }
        recorded <- calls.get
      } yield assertTrue(result == List(42, 8), recorded == List(List(8)))
      ZIO.fromFuture(_ => program.unsafeToFuture())
    }
  )
}
