/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import zio.*
import zio.query.ZQuery

import scala.collection.IterableOps

trait testZQuerySyntax[R] {
  this: bifunctor.module[ZQuery[R, +*, +*]] =>

  protected def toZQueryCacheImpl(cache: Cache)(implicit trace: zio.Trace): UIO[zio.query.Cache]

  extension [Rel, In, E, Out](rel: Rel & Relation[In, Out]) {
    def toZIO(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Out] =
      toQuery(in).run

    def toZIO(in: In, cache: Cache)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Out] =
      toZQueryCacheImpl(cache).flatMap { zCache =>
        toQuery(in).runCache(zCache)
      }

    def toZIOMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Coll[Out]] =
      toQueryMany(in).run

    def toZIOMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Coll[Out]] =
      toZQueryCacheImpl(cache).flatMap { zCache =>
        toQueryMany(in).runCache(zCache)
      }

    def startingFrom(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Out] =
      toZIO(in)

    def startingFrom(in: In, cache: Cache)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Out] =
      toZIO(in, cache)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Coll[Out]] =
      toZIOMany(in)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Coll[Out]] =
      toZIOMany(in, cache)

    def toQuery(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZQuery[R, E, Out] =
      proof.reify.apply(in)

    def toQueryMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZQuery[R, E, Coll[Out]] =
      proof.reify.applyMultiple(in)

    def startingFromQuery(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZQuery[R, E, Out] =
      toQuery(in)

    def startingFromQuery[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZQuery[R, E, Coll[Out]] =
      toQueryMany(in)
  }

  extension [In, E, Out](rel: ReifiedRelation[In, E, Out]) {
    def toZIO(in: In): ZIO[R, E, Out] =
      toQuery(in).run

    def toZIO(in: In, cache: Cache): ZIO[R, E, Out] =
      toZQueryCacheImpl(cache).flatMap { zCache =>
        toQuery(in).runCache(zCache)
      }

    def toZIOMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): ZIO[R, E, Coll[Out]] =
      toQueryMany(in).run

    def toZIOMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    ): ZIO[R, E, Coll[Out]] =
      toZQueryCacheImpl(cache).flatMap { zCache =>
        toQueryMany(in).runCache(zCache)
      }

    def startingFrom(in: In): ZIO[R, E, Out] =
      toZIO(in)

    def startingFrom(in: In, cache: Cache): ZIO[R, E, Out] =
      toZIO(in, cache)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): ZIO[R, E, Coll[Out]] =
      toZIOMany(in)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    ): ZIO[R, E, Coll[Out]] =
      toZIOMany(in, cache)

    def toQuery(in: In): ZQuery[R, E, Out] =
      rel(in)

    def toQueryMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): ZQuery[R, E, Coll[Out]] =
      rel.applyMultiple(in)

    def startingFromQuery(in: In): ZQuery[R, E, Out] =
      toQuery(in)

    def startingFromQuery[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): ZQuery[R, E, Coll[Out]] =
      toQueryMany(in)
  }
}
