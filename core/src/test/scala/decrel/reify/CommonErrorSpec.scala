/*
 * Copyright (c) Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import zio.test.*

object CommonErrorSpec extends ZIOSpecDefault {
  sealed trait Failure
  case object LeftFailure  extends Failure
  case object RightFailure extends Failure

  private def join[A, B, E](left: A, right: B)(implicit errors: CommonError[A, B, E]): (E, E) =
    (errors.left(left), errors.right(right))

  override def spec: Spec[Environment, Any] = suite("common error")(
    test("an infallible child keeps the other child's error type") {
      val leftInfallible  = implicitly[CommonError[Nothing, Int, Int]]
      val rightInfallible = implicitly[CommonError[Int, Nothing, Int]]
      assertTrue(leftInfallible.right(7) == 7, rightInfallible.left(8) == 8)
    },
    test("a common supertype works in either child order") {
      val leftWider  = implicitly[CommonError[AnyVal, Int, AnyVal]]
      val rightWider = implicitly[CommonError[Int, AnyVal, AnyVal]]
      assertTrue(
        leftWider.left(true) == true,
        leftWider.right(7) == 7,
        rightWider.left(8) == 8,
        rightWider.right(false) == false
      )
    },
    test("independent child types infer a shared error without changing either value") {
      val inferred                   = join(LeftFailure, RightFailure)
      val errors: (Failure, Failure) = inferred
      assertTrue(errors._1 eq LeftFailure, errors._2 eq RightFailure)
    }
  )
}
