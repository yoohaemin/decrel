/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.bifunctor

import scala.collection.BuildFrom

trait access { self =>

  /**
   * An `Access` datatype represents an action, when evaluated, brings a value of type A into memory.
   * The datatype needs to be monadic for the derivation mechanism to work.
   *
   * The `bifunctor` package supports `Access` values that are bifunctors (covariant on both type parameters, i.e. not profunctors),
   * but does not place any assumption on the meaning of the first type parameter.
   */
  type Access[+E, +A]

  /**
   * Good ol' `flatMap`.
   *
   * To traverse a relation graph edge by edge is sequential computation, so we require `flatMap` to be implemented
   * on `Access`.
   */
  protected def flatMap[E, A, B](access: Access[E, A])(f: A => Access[E, B]): Access[E, B]

  /**
   * Plain ol' `map`.
   *
   * Can be implemented in terms of `flatMap` and `succeed`, but probably a bad idea considering what kinds of datatypes
   * will be used to implement `Access`.
   */
  protected def map[E, A, B](access: Access[E, A])(f: A => B): Access[E, B]

  /**
   * aka `pure`, `point`, ...
   */
  protected def succeed[A](a: A): Access[Nothing, A]

  /**
   * aka `traverse`. Preserve the collection's shape, order, and one result per input.
   *
   * If you are implementing `Access` with your own datatype, and if you want batching/parallel behavior,
   * please override this default behavior with a more efficient version that comes with your datatype.
   *
   * See `zquery` or `fetch` modules for examples.
   */
  protected def foreach[
    Coll[+T] <: Iterable[T],
    E,
    A,
    B
  ](
    collection: Coll[A]
  )(
    f: A => Access[E, B]
  )(implicit
    bf: BuildFrom[Coll[A], B, Coll[B]]
  ): Access[E, Coll[B]] =
    map(
      collection
        .foldLeft[Access[E, List[B]]](
          succeed(List.empty[B])
        )((values, a) => flatMap(values)(bs => map(f(a))(b => b :: bs)))
    )(values => bf.fromSpecific(collection)(values.reverse))

  // foreach preserves positions, so these casts recover the original branch types.
  private[bifunctor] final def zipWith[E, A, B, C](left: => Access[E, A], right: => Access[E, B])(
    f: (A, B) => C
  ): Access[E, C] =
    map(foreach(List[Access[E, Any]](left, right))(identity)) {
      case List(a, b) => f(a.asInstanceOf[A], b.asInstanceOf[B])
      case _ => throw new IllegalStateException("foreach must preserve the number of results")
    }

  /*
   * TODO Additional allocation imposed to the users because of this syntax is not worth it IMO, but I want
   *  the convenience at the moment.
   *  Delete this and use the plain version above when the codebase is stabilized.
   */
  private[bifunctor] final implicit class AccessSyntax[E, A](private val access: Access[E, A]) {

    def flatMap[E1 >: E, B](f: A => Access[E1, B]): Access[E1, B] =
      self.flatMap[E1, A, B](access)(f)

    def map[B](f: A => B): Access[E, B] =
      self.map(access)(f)
  }
}
