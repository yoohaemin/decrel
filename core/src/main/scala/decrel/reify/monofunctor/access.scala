/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.monofunctor

import scala.collection.{ mutable, BuildFrom }

trait access { self =>

  /**
   * An `Access` datatype represents an action, when evaluated, brings a value of type A into memory.
   * The datatype needs to be monadic for the derivation mechanism to work.
   */
  type Access[A]

  /**
   * Good ol' `flatMap`.
   *
   * To traverse a relation graph edge by edge is sequential computation, so we require `flatMap` to be implemented
   * on `Access`.
   */
  protected def flatMap[A, B](access: Access[A])(f: A => Access[B]): Access[B]

  /**
   * Plain ol' `map`.
   *
   * Can be implemented in terms of `flatMap` and `succeed`, but probably a bad idea considering what kinds of datatypes
   * will be used to implement `Access`.
   */
  protected def map[A, B](access: Access[A])(f: A => B): Access[B]

  /**
   * aka `pure`, `point`, ...
   */
  protected def succeed[A](a: A): Access[A]

  /**
   * aka `traverse`.
   *
   * If you are implementing `Access` with your own datatype, and if you want batching/parallel behavior,
   * please override this default behavior with a more efficient version that comes with your datatype.
   *
   * See `zquery` or `fetch` modules for examples.
   */
  protected def foreach[
    Coll[+T] <: Iterable[T],
    A,
    B
  ](
    collection: Coll[A]
  )(
    f: A => Access[B]
  )(implicit
    bf: BuildFrom[Coll[A], B, Coll[B]]
  ): Access[Coll[B]] =
    map(
      collection
        .foldLeft[Access[mutable.Builder[B, Coll[B]]]](
          succeed(bf.newBuilder(collection))
        )((builder, a) => zipWith(builder, f(a))(_ += _))
    )(_.result())

  private[this] def zipWith[A, B, C](left: => Access[A], right: => Access[B])(
    f: (A, B) => C
  ): Access[C] =
    flatMap(left)(a => map(right)(b => f(a, b)))

  /*
   * TODO Additional allocation imposed to the users because of this syntax is not worth it IMO, but I want
   *  the convenience at the moment.
   *  Delete this and use the plain version above when the codebase is stabilized.
   */
  private[monofunctor] final implicit class AccessSyntax[A](private val access: Access[A]) {

    def flatMap[B](f: A => Access[B]): Access[B] =
      self.flatMap[A, B](access)(f)

    def map[B](f: A => B): Access[B] =
      self.map(access)(f)
  }
}
