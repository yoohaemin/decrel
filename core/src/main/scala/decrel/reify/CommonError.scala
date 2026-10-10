/*
 * Copyright (c) Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

/** Infer a common error type after both child error types have been found. */
sealed trait CommonError[-A, -B, +E] {
  val left: A <:< E
  val right: B <:< E
}

object CommonError {
  implicit def same[E]: CommonError[E, E, E] = new CommonError[E, E, E] {
    override val left: E <:< E  = implicitly
    override val right: E <:< E = implicitly
  }
}
