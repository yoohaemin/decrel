/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

trait catsMonad[F[_]] extends monofunctor.module[F] {
  protected val F: cats.Monad[F]

  protected def flatMap[A, B](access: F[A])(f: A => F[B]): F[B] =
    F.flatMap(access)(f)

  protected def map[A, B](access: F[A])(f: A => B): F[B] =
    F.map(access)(f)

  protected def succeed[A](a: A): F[A] =
    F.pure(a)
}
