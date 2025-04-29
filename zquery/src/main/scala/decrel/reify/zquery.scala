/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import izumi.reflect.Tag
import zio.*
import zio.query.{ CompletedRequestMap, DataSource, ZQuery }

import scala.collection.{ mutable, BuildFrom, IterableOps }

trait zquery[R] extends bifunctor.module[ZQuery[R, +*, +*]] with zquerySyntax[R] {

  // ****** Implementations for Required Operations **************************

  override protected def flatMap[E, A, B](query: ZQuery[R, E, A])(
    f: A => ZQuery[R, E, B]
  ): ZQuery[R, E, B] =
    query.flatMap(f)

  override protected def map[E, A, B](query: ZQuery[R, E, A])(f: A => B): ZQuery[R, E, B] =
    query.map(f)

  override protected def succeed[A](a: A): ZQuery[R, Nothing, A] =
    ZQuery.succeed(a)

  override protected def foreach[Coll[+T] <: Iterable[T], E, A, B](
    collection: Coll[A]
  )(
    f: A => ZQuery[R, E, B]
  )(implicit
    bf: BuildFrom[Coll[A], B, Coll[B]]
  ): Access[E, Coll[B]] =
    ZQuery.foreachBatched(collection)(f)

  // ****** Datasource Implementations ************************************

  private case class RelationRequest[Rel, Id, E, Result](
    rel: Rel & Relation[Id, Result],
    id: Id
  ) extends zio.query.Request[E, Result]

  private def buildDatasource[Rel: Tag, In, E, Out](
    rel: Rel
  )(
    batchExecute: Chunk[In] => ZIO[R, E, Chunk[(In, Out)]]
  ): DataSource[R, RelationRequest[Rel, In, E, Out]] =
    new DataSource.Batched[R, RelationRequest[Rel, In, E, Out]] {
      override val identifier: String = "RelationDatasource:" + Tag[Rel].tag.longNameInternalSymbol

      override def run(
        requests: Chunk[RelationRequest[Rel, In, E, Out]]
      )(implicit
        trace: Trace
      ): ZIO[R, Nothing, CompletedRequestMap] = {
        val deduplicated = requests.distinctBy(_.id)

        batchExecute(deduplicated.map(_.id)).flatMap { results =>
          val mapBuilder = mutable.Map.newBuilder[In, Exit[E, Out]]
          mapBuilder.sizeHint(results)
          val resultsMap = mapBuilder
            .addAll(results.view.map(pair => pair._1 -> Exit.succeed(pair._2)))
            .result()
            .withDefault(in =>
              Exit.die(new NoSuchElementException(s"Response for request not found: $in"))
            )

          ZIO.succeed(
            CompletedRequestMap.fromIterableWith[E, RelationRequest[Rel, In, E, Out], Out](
              requests
            )(
              (a: zio.query.Request[E, Out]) => a,
              request => resultsMap(request.id)
            )
          )
        }.catchAll { e =>
          val failure = Exit.fail(e)
          ZIO.succeed(
            CompletedRequestMap.fromIterableWith(requests)(identity, _ => failure)
          )
        }
      }
    }

  // batchExecute should return a list of results that contains all of the identifiers of the provided list
  // Failing to do so will result in fiber death
  // order does not matter
  def implementSingleDatasource[Rel: Tag, In, E, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: Chunk[In] => ZIO[R, E, Chunk[(In, Out)]]
  )(implicit
    tag: Tag[Out]
  ): Proof.Single[Rel & Relation.Single[In, Out], In, E, Out] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, E, Out] {

      private val ds = buildDatasource[Rel, In, E, Out](relation)(batchExecute)

      override val reify: ReifiedRelation[In, E, Out] =
        new ReifiedRelation.Custom[In, E, Out] {
          override def apply(in: In): ZQuery[R, E, Out] =
            applyMultiple(List(in)).map(_.head) // TODO add tests for this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): ZQuery[R, E, Coll[Out]] =
            ZQuery.foreachPar(ins) { in =>
              ZQuery.fromRequest[R, E, RelationRequest[Rel, In, E, Out], Out](
                RelationRequest[Rel, In, E, Out](relation, in)
              )(ds)
            }
        }
    }

  def implementOptionalDatasource[Rel: Tag, In, E, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: Chunk[In] => ZIO[R, E, Chunk[(In, Option[Out])]]
  )(implicit
    tag: Tag[Out]
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, E, Out] =
    new Proof.Optional[Rel & Relation.Optional[In, Out], In, E, Out] {

      private val ds = buildDatasource[Rel, In, E, Option[Out]](relation)(batchExecute)

      override val reify: ReifiedRelation[In, E, Option[Out]] =
        new ReifiedRelation.Custom[In, E, Option[Out]] {
          override def apply(in: In): ZQuery[R, E, Option[Out]] =
            applyMultiple(List(in)).map(_.head) // TODO This should be safe... let's test this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): ZQuery[R, E, Coll[Option[Out]]] =
            ZQuery.foreachPar(ins) { in =>
              ZQuery.fromRequest[R, E, RelationRequest[Rel, In, E, Option[Out]], Option[Out]](
                RelationRequest(relation, in)
              )(ds)
            }
        }
    }

  def implementManyDatasource[
    Rel: Tag,
    In,
    E,
    CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
    Out
  ](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: Chunk[In] => ZIO[R, E, Chunk[(In, CC[Out])]]
  )(implicit
    tag: Tag[CC[Out]]
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, E, CC, Out] =
    new Proof.Many[Rel & Relation.Many[In, CC, Out], In, E, CC, Out] {

      private val ds = buildDatasource[Rel, In, E, CC[Out]](relation)(batchExecute)

      override val reify: ReifiedRelation[In, E, CC[Out]] =
        new ReifiedRelation.Custom[In, E, CC[Out]] {
          override def apply(in: In): ZQuery[R, E, CC[Out]] =
            applyMultiple(List(in)).map(_.head) // TODO This should be safe... let's test this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): ZQuery[R, E, Coll[CC[Out]]] =
            ZQuery.foreachPar(ins) { in =>
              ZQuery.fromRequest[R, E, RelationRequest[Rel, In, E, CC[Out]], CC[Out]](
                RelationRequest(relation, in)
              )(ds)
            }
        }
    }

  def implementCustomDatasource[
    Tree,
    In,
    E,
    Out
  ](
    relation: Relation.Custom[Tree, In, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: Chunk[In] => ZIO[R, E, Chunk[(In, Out)]]
  )(implicit
    tag: Tag[Relation.Custom[Tree, In, Out]]
  ): Proof[Relation.Custom[Tree, In, Out], In, E, Out] =
    new Proof[Relation.Custom[Tree, In, Out], In, E, Out] {
      private val ds = buildDatasource(relation)(batchExecute)

      override val reify: ReifiedRelation[In, E, Out] =
        new ReifiedRelation.Custom[In, E, Out] {
          override def apply(in: In): ZQuery[R, E, Out] =
            applyMultiple(List(in)).map(_.head) // TODO This should be safe... let's test this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): ZQuery[R, E, Coll[Out]] =
            ZQuery.foreachPar(ins) { in =>
              ZQuery.fromRequest[R, E, RelationRequest[
                Relation.Custom[Tree, In, Out],
                In,
                E,
                Out
              ], Out](RelationRequest(relation, in))(ds)
            }
        }
    }

  def contramapOneProof[Rel, NewRel, In, E, Out, B](
    proof: Proof[Rel, In, E, Out],
    rel: NewRel & Relation.Single[B, Out],
    f: B => In
  ): Proof.Single[NewRel & Relation.Single[B, Out], B, E, Out] =
    new Proof.Single[NewRel & Relation.Single[B, Out], B, E, Out] {
      override val reify: ReifiedRelation[B, E, Out] =
        new ReifiedRelation.Custom[B, E, Out] {
          override def apply(in: B): ZQuery[R, E, Out] =
            proof.reify.apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[E, Coll[Out]] =
            proof.reify.applyMultiple[Coll](in.map(f))
        }
    }

  def contramapOptionalProof[
    Rel,
    NewRel,
    In,
    E,
    Out,
    B
  ](
    proof: Proof[Rel, In, E, Out],
    rel: NewRel & Relation.Optional[B, Out],
    f: B => Option[In]
  ): Proof.Optional[NewRel & Relation.Optional[B, Out], B, E, Out] =
    new Proof.Optional[NewRel & Relation.Optional[B, Out], B, E, Out] {
      override val reify: ReifiedRelation[B, E, Option[Out]] =
        new ReifiedRelation.Custom[B, E, Option[Out]] {

          override def apply(in: B): ZQuery[R, E, Option[Out]] =
            proof.reify.applyMultiple(f(in).toList).map(_.headOption)

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[E, Coll[Option[Out]]] =
            // TODO how to optimize?
            ZQuery.foreachBatched(in)(b => apply(b))
        }
    }

  def contramapManyProof[
    Rel,
    NewRel,
    In,
    E,
    Out,
    B,
    CC[+T] <: Iterable[T] & IterableOps[T, CC, CC[T]]
  ](
    proof: Proof[Rel, In, E, Out],
    rel: NewRel & Relation.Many[B, CC, Out],
    f: B => CC[In]
  ): Proof.Many[NewRel & Relation.Many[B, CC, Out], B, E, CC, Out] =
    new Proof.Many[NewRel & Relation.Many[B, CC, Out], B, E, CC, Out] {
      override val reify: ReifiedRelation[B, E, CC[Out]] =
        new ReifiedRelation.Custom[B, E, CC[Out]] {

          override def apply(in: B): ZQuery[R, E, CC[Out]] =
            proof.reify.applyMultiple(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[E, Coll[CC[Out]]] =
            // TODO how to optimize?
            ZQuery.foreachBatched(in) { b =>
              proof.reify.applyMultiple(f(b))
            }
        }
    }

  // ****** Cache Implementation ************************************

  implicit class CacheOps(private val cache: Cache) {
    def toZQueryCache(implicit trace: zio.Trace): UIO[zio.query.Cache] =
      toZQueryCacheImpl(cache)
  }

  private def toZQueryCacheImpl(cache: Cache)(implicit trace: zio.Trace): UIO[zio.query.Cache] =
    zio.query.Cache.empty.flatMap { zCache =>
      ZIO.foldLeft(cache.entries)(zCache) { case (zCache, (_, v)) =>
        val k: v.key.type                               = v.key
        val relation: k.R & Relation[k.Input, k.Result] = k.relationEv(k._relation)
        val key: k.Input                                = k._key
        val value: k.Result                             = v.valueEv(v._value)
        Promise
          .make[Nothing, k.Result]
          .flatMap { promise =>
            promise.succeed(value) *>
              zCache.put(
                RelationRequest[k.R, k.Input, Nothing, k.Result](relation, key),
                promise
              )
          }
          .as(zCache)
      }
    }

  // ****** Syntax ************************************

  /**
   * Syntax for Relation values
   */
  implicit class ZQueryRelationOps[Rel, In, E, Out](private val rel: Rel & Relation[In, Out]) { // TODO add AnyVal

    def startingFrom(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Out] =
      startingFromQuery(in).run

    def startingFrom(in: In, cache: Cache)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Out] =
      toZQueryCacheImpl(cache).flatMap { zCache =>
        startingFromQuery(in).runCache(zCache)
      }

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Coll[Out]] =
      startingFromQuery(in).run

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Coll[Out]] =
      toZQueryCacheImpl(cache).flatMap { zCache =>
        startingFromQuery(in).runCache(zCache)
      }

    def startingFromQuery(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZQuery[R, E, Out] =
      proof.reify.apply(in)

    def startingFromQuery[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZQuery[R, E, Coll[Out]] =
      proof.reify.applyMultiple(in)
  }

  // /**
  //  * Syntax for expand
  //  */
  // implicit class ZQueryRelationOpsExpand[In](private val in: In) { // TODO add AnyVal

  //   def expand[Rel, E, Out](
  //     rel: Rel & Relation[In, Out]
  //   )(implicit
  //     proof: Proof[Rel & Relation[In, Out], In, E, Out]
  //   ): ZIO[R, E, Out] =
  //     proof.reify.apply(in).run

  // }

  implicit class RefCacheOps(private val refCache: Ref[Cache]) {

    def add[Rel, A, B](relation: Rel & Relation[A, B], key: A, value: B)(implicit
      tag: Tag[Rel]
    ): UIO[Unit] =
      refCache.update(_.add[Rel, A, B](relation, key, value))

  }
}

object zquery extends zquery[Any]
