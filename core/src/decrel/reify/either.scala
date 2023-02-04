/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

trait either extends bifunctor.module[Either] {

  override protected def flatMap[E, A, B](access: Either[E, A])(
    f: A => Either[E, B]
  ): Either[E, B] =
    access.flatMap(f)

  override protected def map[E, A, B](access: Either[E, A])(f: A => B): Either[E, B] =
    access.map(f)

  override protected def succeed[A](a: A): Either[Nothing, A] =
    Right(a)

}

object either extends either
