/*
 * Copyright (c) 2025 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import kyo.*

import decrel.Relation

import scala.collection.immutable.HashMap
import scala.collection.{ mutable, BuildFrom, IterableOps }
import scala.util.control.NoStackTrace

/**
 * Instantiate this trait in one place in your app pass around the object, importing it where you want to use it.
 *
 * @tparam Eff Underlying effect type, usually `cats.effect.IO` or similar.
 */
trait kyoGeneric[Eff] extends monofunctor.module[[A] =>> A < Eff] { self =>

  override final protected def flatMap[A, B](access: A < Eff)(f: A => B < Eff): Access[B] =
    access.flatMap(f)

  override final protected def map[A, B](access: A < Eff)(f: A => B): Access[B] =
    access.flatMap(a => f(a): Access[B])

  override final protected def succeed[A](a: A): Access[A] =
    a

}
