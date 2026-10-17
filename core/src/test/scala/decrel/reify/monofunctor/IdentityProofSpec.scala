/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.monofunctor

import decrel.*
import zio.test.*

object IdentityProofSpec extends ZIOSpecDefault {
  case object First  extends Relation.Single[Int, String]
  case object Second extends Relation.Single[String, Int]
  case object Self   extends Relation.Self[Int]
  class Interpreter  extends module[Option] {
    protected def succeed[A](value: A): Option[A]                               = Some(value)
    protected def map[A, B](value: Option[A])(f: A => B): Option[B]             = value.map(f)
    protected def flatMap[A, B](value: Option[A])(f: A => Option[B]): Option[B] = value.flatMap(f)
    var calls                                                 = Vector.empty[String]
    implicit val first: Proof.Single[First.type, Int, String] =
      new Proof.Single[First.type, Int, String] {
        val reify: ReifiedRelation[Int, String] =
          new ReifiedRelation.FromFunction[Int, String](i => {
            calls = calls :+ "first"
            i.toString
          })
      }
    implicit val second: Proof.Single[Second.type, String, Int] =
      new Proof.Single[Second.type, String, Int] {
        val reify: ReifiedRelation[String, Int] =
          new ReifiedRelation.FromFunction[String, Int](s => {
            calls = calls :+ "second"
            s.toInt + 100
          })
      }
  }
  private def checked(label: String)(result: => TestResult): Spec[Any, Throwable] =
    test(label)(zio.ZIO.fromFuture(ec => scala.concurrent.Future(result)(ec)))

  def spec: Spec[Environment, Any] = suite("relation identity - monofunctor")(
    checked("same endpoint types do not turn a composed relation into identity") {
      val interpreter = new Interpreter
      import interpreter.*
      val path   = First >>: Second
      val zipped = path & path
      val result = zipped.reify.apply(1)
      assertTrue(
        result == Some((101, 101)),
        calls.count(_ == "first") == 2,
        calls.count(_ == "second") == 2
      )
    },
    checked("declared and constructed Self relations still derive identity") {
      val interpreter = new Interpreter
      import interpreter.*
      val declared    = Self.reify.apply(2)
      val constructed = Relation.Self[Int].reify.apply(3)
      assertTrue(declared == Some(2), constructed == Some(3), calls.isEmpty)
    },
    test("a missing proof cannot be replaced with identity") {
      typeCheck("""import decrel._
        val interpreter = new decrel.reify.monofunctor.IdentityProofSpec.Interpreter; import interpreter._
        case object Missing extends Relation.Single[Int, Int]
        Missing.reify
      """).flatMap(result =>
        zio.ZIO.fromFuture(ec => scala.concurrent.Future(assertTrue(result.isLeft))(ec))
      )
    }
  )
}
