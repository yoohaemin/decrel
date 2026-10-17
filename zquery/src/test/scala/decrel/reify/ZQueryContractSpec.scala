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
import zio.test.*

object ZQueryContractSpec extends ZIOSpecDefault {
  case object Single   extends Relation.Single[Int, String]
  case object Optional extends Relation.Optional[Int, String]
  case object Many     extends Relation.Many[Int, Vector, String]
  val Custom = Single.customImpl

  class Interpreter(f: Chunk[Int] => UIO[Chunk[(Int, String)]]) extends decrel.reify.zquery[Any] {
    implicit val single: Proof.Single[Single.type, Int, Nothing, String] =
      implementSingleDatasource(Single)(f)
  }
  private def violation(result: Exit[Nothing, Any]): Option[ZQueryDatasourceContractException] =
    result.causeOption.toList.flatMap(_.defects).collectFirst {
      case error: ZQueryDatasourceContractException => error
    }

  private def checked(label: String)(result: => UIO[TestResult]): Spec[Any, Throwable] =
    test(label)(ZIO.fromFuture(ec => scala.concurrent.Future(())(ec)) *> result)

  def spec: Spec[Environment, Any] = suite("ZQuery datasource contracts")(
    checked("missing, duplicate and unexpected keys are reported together") {
      val module = new Interpreter(_ => ZIO.succeed(Chunk(1 -> "one", 1 -> "again", 3 -> "extra")))
      import module.*
      Single.startingFrom(List(1, 2)).exit.map { result =>
        assertTrue(
          violation(result).exists(e =>
            e.missingKeys == Set(2) && e.duplicateKeys == Set(1) && e.unexpectedKeys == Set(3)
          )
        )
      }
    },
    checked("malformed singleton results are rejected") {
      val module = new Interpreter(_ => ZIO.succeed(Chunk(2 -> "wrong")))
      import module.*
      Single
        .startingFrom(1)
        .exit
        .map(result => assertTrue(violation(result).exists(_.missingKeys == Set(1))))
    },
    checked("duplicates alone are rejected instead of choosing the last value") {
      val module = new Interpreter(_ => ZIO.succeed(Chunk(1 -> "one", 1 -> "two")))
      import module.*
      Single
        .startingFrom(1)
        .exit
        .map(result => assertTrue(violation(result).exists(_.duplicateKeys == Set(1))))
    },
    checked("unexpected keys alone are rejected") {
      val module = new Interpreter(_ => ZIO.succeed(Chunk(1 -> "one", 2 -> "extra")))
      import module.*
      Single
        .startingFrom(1)
        .exit
        .map(result => assertTrue(violation(result).exists(_.unexpectedKeys == Set(2))))
    },
    checked("input duplicates are deduplicated and reordered output preserves input positions") {
      for {
        calls <- Ref.make(List.empty[List[Int]])
        module = new Interpreter(ids =>
          calls.update(_ :+ ids.toList).as(Chunk(2 -> "two", 1 -> "one"))
        )
        result  <- { import module.*; Single.startingFrom(Vector(1, 2, 1)) }
        batches <- calls.get
      } yield assertTrue(result == Vector("one", "two", "one"), batches == List(List(1, 2)))
    },
    checked("empty input does not invoke a datasource") {
      val module = new Interpreter(_ => ZIO.dieMessage("unexpected callback"))
      import module.*
      Single.startingFrom(List.empty[Int]).map(result => assertTrue(result.isEmpty))
    },
    checked("an explicit None is valid but an omitted Optional key is a contract defect") {
      class OptionalInterpreter(omit: Boolean) extends decrel.reify.zquery[Any] {
        implicit val optional: Proof.Optional[Optional.type, Int, Nothing, String] =
          implementOptionalDatasource(Optional)(ids =>
            ZIO.succeed(if (omit) Chunk.empty else ids.map(_ -> Option.empty[String]))
          )
      }
      val valid   = new OptionalInterpreter(false)
      val invalid = new OptionalInterpreter(true)
      for {
        absent  <- { import valid.*; Optional.startingFrom(1) }
        omitted <- { import invalid.*; Optional.startingFrom(1).exit }
      } yield assertTrue(absent.isEmpty, violation(omitted).exists(_.missingKeys == Set(1)))
    },
    checked("Many and Custom singleton paths preserve their complete result") {
      class OtherInterpreter extends decrel.reify.zquery[Any] {
        implicit val many: Proof.Many[Many.type, Int, Nothing, Vector, String] =
          implementManyDatasource(Many)(ids => ZIO.succeed(ids.map(_ -> Vector.empty[String])))
        val custom =
          implementCustomDatasource(Custom)(ids => ZIO.succeed(ids.map(i => i -> s"custom$i")))
      }
      val module = new OtherInterpreter
      import module.*
      for {
        manyResult   <- Many.startingFrom(1)
        customResult <- Custom.startingFrom(1)(custom)
      } yield assertTrue(manyResult.isEmpty, customResult == "custom1")
    },
    checked("typed batch failures remain typed errors") {
      class Failing extends decrel.reify.zquery[Any] {
        implicit val single: Proof.Single[Single.type, Int, String, String] =
          implementSingleDatasource(Single)(_ => ZIO.fail("offline"))
      }
      val module = new Failing
      import module.*
      Single.startingFrom(List(1, 2)).either.map(result => assertTrue(result == Left("offline")))
    }
  )
}
