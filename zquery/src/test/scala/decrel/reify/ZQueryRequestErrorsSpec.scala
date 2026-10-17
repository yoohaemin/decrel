/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import zio.*
import zio.query.ZQuery
import zio.test.*

object ZQueryRequestErrorsSpec extends ZIOSpecDefault {
  case object Single   extends Relation.Single[Int, String]
  case object Optional extends Relation.Optional[Int, String]
  case object Many     extends Relation.Many[Int, Vector, String]
  case object Next     extends Relation.Single[String, Boolean]
  val Custom = Single.customImpl

  class Interpreter(calls: Ref[List[List[Int]]], batchError: Boolean = false)
      extends decrel.reify.zquery[Any] {
    implicit val single: Proof.Single[Single.type, Int, String, String] =
      implementSingleDatasourceEither(Single) { ids =>
        calls.update(_ :+ ids.toList) *>
          (if (batchError) ZIO.fail("offline")
           else
             ZIO.succeed(ids.map { id =>
               val result: Either[String, String] =
                 if (id == 2) Left("missing") else Right(s"value$id")
               id -> result
             }))
      }
  }

  class ChainedInterpreter(calls: Ref[List[List[Int]]], nextCalls: Ref[List[String]])
      extends Interpreter(calls) {
    implicit val next: Proof.Single[Next.type, String, String, Boolean] =
      implementSingleDatasource(Next)(ids =>
        nextCalls.update(_ ++ ids.toList).as(ids.map(_ -> true))
      )
  }

  private def checked[E](label: String)(result: => ZIO[Any, E, TestResult]): Spec[Any, Any] =
    test(label)(ZIO.fromFuture(ec => scala.concurrent.Future(())(ec)) *> result)

  def spec: Spec[Environment, Any] = suite("ZQuery per-request errors")(
    checked("successful keys survive a sibling failure when recovered per request") {
      for {
        calls <- Ref.make(List.empty[List[Int]])
        module = new Interpreter(calls)
        result <- {
          import module.*;
          ZQuery.foreachBatched(List(1, 2, 1, 3))(id => Single.startingFromQuery(id).either).run
        }
        batches <- calls.get
      } yield assertTrue(
        result == List(Right("value1"), Left("missing"), Right("value1"), Right("value3")),
        batches.size == 1,
        batches.head.toSet == Set(1, 2, 3)
      )
    },
    checked("unrecovered bulk queries still fail with the affected key's domain error") {
      for {
        calls <- Ref.make(List.empty[List[Int]])
        module = new Interpreter(calls)
        result <- { import module.*; Single.startingFrom(List(1, 2, 3)).either }
      } yield assertTrue(result == Left("missing"))
    },
    checked("a batch-wide effect failure fails every request") {
      for {
        calls <- Ref.make(List.empty[List[Int]])
        module = new Interpreter(calls, true)
        result <- {
          import module.*;
          ZQuery.foreachBatched(List(1, 2))(id => Single.startingFromQuery(id).either).run
        }
        batches <- calls.get
      } yield assertTrue(result == List(Left("offline"), Left("offline")), batches.size == 1)
    },
    checked("Optional absence and a domain failure remain distinct") {
      class Module extends decrel.reify.zquery[Any] {
        implicit val optional: Proof.Optional[Optional.type, Int, String, String] =
          implementOptionalDatasourceEither(Optional)(ids =>
            ZIO.succeed(ids.map { id =>
              val result: Either[String, Option[String]] = id match {
                case 1 => Right(None)
                case 2 => Left("denied")
                case _ => Right(Some("present"))
              }
              id -> result
            })
          )
      }
      val module = new Module
      import module.*
      ZQuery
        .foreachBatched(List(1, 2, 3))(id => Optional.startingFromQuery(id).either)
        .run
        .map(result =>
          assertTrue(result == List(Right(None), Left("denied"), Right(Some("present"))))
        )
    },
    checked("Many preserves empty and nonempty collections alongside failures") {
      class Module extends decrel.reify.zquery[Any] {
        implicit val many: Proof.Many[Many.type, Int, String, Vector, String] =
          implementManyDatasourceEither(Many)(ids =>
            ZIO.succeed(ids.map { id =>
              val result: Either[String, Vector[String]] = id match {
                case 1 => Right(Vector.empty)
                case 2 => Left("denied")
                case _ => Right(Vector("a", "b"))
              }
              id -> result
            })
          )
      }
      val module = new Module
      import module.*
      ZQuery
        .foreachBatched(List(1, 2, 3))(id => Many.startingFromQuery(id).either)
        .run
        .map(result =>
          assertTrue(result == List(Right(Vector.empty), Left("denied"), Right(Vector("a", "b"))))
        )
    },
    checked("Custom relations can return per-request errors") {
      class Module extends decrel.reify.zquery[Any] {
        val custom = implementCustomDatasourceEither(Custom)(ids =>
          ZIO.succeed(ids.map { id =>
            val result: Either[String, String] =
              if (id == 2) Left("custom-error") else Right("custom-value")
            id -> result
          })
        )
      }
      val module = new Module
      import module.*
      ZQuery
        .foreachBatched(List(1, 2))(id => Custom.startingFromQuery(id)(custom).either)
        .run
        .map(result => assertTrue(result == List(Right("custom-value"), Left("custom-error"))))
    },
    checked("a failed parent skips its dependent request while successful parents proceed") {
      for {
        calls     <- Ref.make(List.empty[List[Int]])
        nextCalls <- Ref.make(List.empty[String])
        module = new ChainedInterpreter(calls, nextCalls)
        result <- {
          import module.*
          val query = Single >>: Next
          ZQuery.foreachBatched(List(1, 2))(id => query.startingFromQuery(id).either).run
        }
        downstream <- nextCalls.get
      } yield assertTrue(result == List(Right(true), Left("missing")), downstream == List("value1"))
    },
    checked("per-request constructors retain strict batch-key validation") {
      class Module extends decrel.reify.zquery[Any] {
        implicit val single: Proof.Single[Single.type, Int, String, String] =
          implementSingleDatasourceEither(Single)(_ =>
            ZIO.succeed(Chunk(1 -> Left("domain-error")))
          )
      }
      val module = new Module
      import module.*
      Single
        .startingFrom(List(1, 2))
        .exit
        .map(result =>
          assertTrue(
            result.causeOption
              .exists(_.defects.exists(_.isInstanceOf[ZQueryDatasourceContractException]))
          )
        )
    }
  )
}
