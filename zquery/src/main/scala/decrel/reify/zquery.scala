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

  private case class RelationRequest[Id, E, Result](
    relationKey: Any,
    id: Id
  ) extends zio.query.Request[E, Result]

  private val datasourceIdentifiers = new WeakKeyCache[String]
  private var nextDatasourceId      = 0L

  private def datasourceIdentifier(relationKey: Any): String =
    datasourceIdentifiers.getOrCreate(relationKey) {
      nextDatasourceId += 1
      s"RelationDatasource:$nextDatasourceId"
    }

  private def buildDatasource[In, E, Out](
    relationKey: Any
  )(
    batchExecute: Chunk[In] => ZIO[R, E, Chunk[(In, Out)]]
  ): DataSource[R, RelationRequest[In, E, Out]] =
    new DataSource.Batched[R, RelationRequest[In, E, Out]] {
      override val identifier: String = datasourceIdentifier(relationKey)

      override def run(
        requests: Chunk[RelationRequest[In, E, Out]]
      )(implicit
        trace: Trace
      ): ZIO[R, Nothing, CompletedRequestMap] = {
        val deduplicated = requests.distinctBy(request => (request.relationKey, request.id))

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
            CompletedRequestMap.fromIterableWith[E, RelationRequest[In, E, Out], Out](
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

  private def singleReifiedRelation[In, E, Out, Filter](
    relationKey: Any,
    filter: Option[Filter]
  )(
    batchExecute: (Chunk[In], Option[Filter]) => ZIO[R, E, Chunk[(In, Out)]]
  ): ReifiedRelation[In, E, Out] = {
    val ds = buildDatasource[In, E, Out](relationKey)(ins => batchExecute(ins, filter))

    new ReifiedRelation.Custom[In, E, Out] {
      override def apply(in: In): ZQuery[R, E, Out] =
        applyMultiple(List(in)).map(_.head)

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        ins: Coll[In]
      ): ZQuery[R, E, Coll[Out]] =
        ZQuery.foreachPar(ins) { in =>
          ZQuery.fromRequest[R, E, RelationRequest[In, E, Out], Out](
            RelationRequest(relationKey, in)
          )(ds)
        }
    }
  }

  private def requiredSingleReifiedRelation[In, E, Out](
    relationKey: Any,
    relation: ReifiedRelation[In, E, Option[Out]]
  ): ReifiedRelation[In, E, Out] =
    new ReifiedRelation.Custom[In, E, Out] {
      override def apply(in: In): ZQuery[R, E, Out] =
        relation.apply(in).map {
          case Some(out) => out
          case None =>
            throw new IllegalStateException(
              s"Single relation implementation returned None for unfiltered access: $relationKey"
            )
        }

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        ins: Coll[In]
      ): ZQuery[R, E, Coll[Out]] =
        relation.applyMultiple(ins).map(
          _.map {
            case Some(out) => out
            case None =>
              throw new IllegalStateException(
                s"Single relation implementation returned None for unfiltered access: $relationKey"
              )
          }
        )
    }

  // batchExecute should return a list of results that contains all of the identifiers of the provided list
  // Failing to do so will result in fiber death
  // order does not matter
  def implementSingleDatasource[Rel, In, E, Out, Filter](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: (Chunk[In], Option[Filter]) => ZIO[R, E, Chunk[(In, Option[Out])]]
  )(implicit d: DummyImplicit): Proof.Single[Rel & Relation.Single[In, Out], In, E, Out, Filter] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, E, Out, Filter] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Single[In, Out]) & Relation[In, Out]
      ): ReifiedRelation[In, E, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[In, E, Out] =
        requiredSingleReifiedRelation(
          filter.fold[Any](relation)(_ => relationKey),
          reifyFilteredOptional(relationKey, filter)
        )

      override private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[In, E, Option[Out]] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementSingleDatasource[Rel, In, E, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: (Chunk[In], Option[Nothing]) => ZIO[R, E, Chunk[(In, Out)]]
  ): Proof.Single[Rel & Relation.Single[In, Out], In, E, Out, Nothing] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, E, Out, Nothing] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Single[In, Out]) & Relation[In, Out]
      ): ReifiedRelation[In, E, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[In, E, Out] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementOptionalDatasource[Rel, In, E, Out, Filter](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: (Chunk[In], Option[Filter]) => ZIO[R, E, Chunk[(In, Option[Out])]]
  )(implicit d: DummyImplicit): Proof.Optional[Rel & Relation.Optional[In, Out], In, E, Out, Filter] =
    new Proof.Optional[Rel & Relation.Optional[In, Out], In, E, Out, Filter] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Optional[In, Out]) & Relation[In, Option[Out]]
      ): ReifiedRelation[In, E, Option[Out]] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[In, E, Option[Out]] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementOptionalDatasource[Rel, In, E, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: (Chunk[In], Option[Nothing]) => ZIO[R, E, Chunk[(In, Option[Out])]]
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, E, Out, Nothing] =
    implementOptionalDatasource[Rel, In, E, Out, Nothing](relation)(batchExecute)

  def implementManyDatasource[
    Rel,
    In,
    E,
    CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
    Out,
    Filter
  ](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    batchExecute: (Chunk[In], Option[Filter]) => ZIO[R, E, Chunk[(In, CC[Out])]]
  )(implicit d: DummyImplicit): Proof.Many[Rel & Relation.Many[In, CC, Out], In, E, CC, Out, Filter] =
    new Proof.Many[Rel & Relation.Many[In, CC, Out], In, E, CC, Out, Filter] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Many[In, CC, Out]) & Relation[In, CC[Out]]
      ): ReifiedRelation[In, E, CC[Out]] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[In, E, CC[Out]] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementManyDatasource[
    Rel,
    In,
    E,
    CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
    Out
  ](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    batchExecute: (Chunk[In], Option[Nothing]) => ZIO[R, E, Chunk[(In, CC[Out])]]
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, E, CC, Out, Nothing] =
    implementManyDatasource[Rel, In, E, CC, Out, Nothing](relation)(batchExecute)

  def implementCustomDatasource[
    Tree,
    In,
    E,
    Out,
    Filter
  ](
    relation: Relation.Custom[Tree, In, Out]
  )(
    batchExecute: (Chunk[In], Option[Filter]) => ZIO[R, E, Chunk[(In, Out)]]
  )(implicit d: DummyImplicit): Proof[Relation.Custom[Tree, In, Out], In, E, Out, Filter] =
    new Proof[Relation.Custom[Tree, In, Out], In, E, Out, Filter] {
      override private[decrel] def reifyRelation(
        relationValue: Relation.Custom[Tree, In, Out] & Relation[In, Out]
      ): ReifiedRelation[In, E, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[In, E, Out] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementCustomDatasource[
    Tree,
    In,
    E,
    Out
  ](
    relation: Relation.Custom[Tree, In, Out]
  )(
    batchExecute: (Chunk[In], Option[Nothing]) => ZIO[R, E, Chunk[(In, Out)]]
  ): Proof[Relation.Custom[Tree, In, Out], In, E, Out, Nothing] =
    implementCustomDatasource[Tree, In, E, Out, Nothing](relation)(batchExecute)

  def contramapOneProof[Rel <: Relation[In, Out], NewRel, In, E, Out, B, Filter](
    proof: Proof.Single[Rel, In, E, Out, Filter],
    rel: NewRel & Relation.Single[B, Out],
    f: B => In
  ): Proof.Single[NewRel & Relation.Single[B, Out], B, E, Out, Filter] =
    new Proof.Single[NewRel & Relation.Single[B, Out], B, E, Out, Filter] {
      override private[decrel] def reifyRelation(
        relation: (NewRel & Relation.Single[B, Out]) & Relation[B, Out]
      ): ReifiedRelation[B, E, Out] =
        reifyFiltered(relation, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[B, E, Out] =
        new ReifiedRelation.Custom[B, E, Out] {
          override def apply(in: B): ZQuery[R, E, Out] =
            proof.reifyFiltered(relationKey, filter).apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[E, Coll[Out]] =
            proof.reifyFiltered(relationKey, filter).applyMultiple[Coll](in.map(f))
        }

      override private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[B, E, Option[Out]] =
        new ReifiedRelation.Custom[B, E, Option[Out]] {
          override def apply(in: B): ZQuery[R, E, Option[Out]] =
            proof.reifyFilteredOptional(relationKey, filter).apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[E, Coll[Option[Out]]] =
            proof.reifyFilteredOptional(relationKey, filter).applyMultiple[Coll](in.map(f))
        }
    }

  def contramapOptionalProof[
    Rel,
    NewRel,
    In,
    E,
    Out,
    B,
    Filter
  ](
    proof: Proof[Rel, In, E, Out, Filter],
    rel: NewRel & Relation.Optional[B, Out],
    f: B => Option[In]
  ): Proof.Optional[NewRel & Relation.Optional[B, Out], B, E, Out, Filter] =
    new Proof.Optional[NewRel & Relation.Optional[B, Out], B, E, Out, Filter] {
      override private[decrel] def reifyRelation(
        relation: (NewRel & Relation.Optional[B, Out]) & Relation[B, Option[Out]]
      ): ReifiedRelation[B, E, Option[Out]] =
        reifyFiltered(relation, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[B, E, Option[Out]] =
        new ReifiedRelation.Custom[B, E, Option[Out]] {

          override def apply(in: B): ZQuery[R, E, Option[Out]] =
            proof.reifyFiltered(relationKey, filter).applyMultiple(f(in).toList).map(_.headOption)

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[E, Coll[Option[Out]]] =
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
    CC[+T] <: Iterable[T] & IterableOps[T, CC, CC[T]],
    Filter
  ](
    proof: Proof[Rel, In, E, Out, Filter],
    rel: NewRel & Relation.Many[B, CC, Out],
    f: B => CC[In]
  ): Proof.Many[NewRel & Relation.Many[B, CC, Out], B, E, CC, Out, Filter] =
    new Proof.Many[NewRel & Relation.Many[B, CC, Out], B, E, CC, Out, Filter] {
      override private[decrel] def reifyRelation(
        relation: (NewRel & Relation.Many[B, CC, Out]) & Relation[B, CC[Out]]
      ): ReifiedRelation[B, E, CC[Out]] =
        reifyFiltered(relation, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[B, E, CC[Out]] =
        new ReifiedRelation.Custom[B, E, CC[Out]] {

          override def apply(in: B): ZQuery[R, E, CC[Out]] =
            proof.reifyFiltered(relationKey, filter).applyMultiple(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[E, Coll[CC[Out]]] =
            ZQuery.foreachBatched(in) { b =>
              proof.reifyFiltered(relationKey, filter).applyMultiple(f(b))
            }
        }
    }

  // ****** Cache Implementation ************************************

  implicit class CacheOps(private val cache: Cache) {
    def toZQueryCache(implicit trace: zio.Trace): UIO[zio.query.Cache] =
      toZQueryCacheImpl(cache)
  }

  override protected def toZQueryCacheImpl(cache: Cache)(implicit trace: zio.Trace): UIO[zio.query.Cache] =
    zio.query.Cache.empty.flatMap { zCache =>
      ZIO.foldLeft(cache.entries)(zCache) { case (zCache, (_, v)) =>
        val k: v.key.type                                = v.key
        val relation: k.R & Relation[k.Input, k.Result] = k.relationEv(k._relation)
        val key: k.Input                                 = k._key
        val value: k.Result                              = v.valueEv(v._value)
        Promise
          .make[Nothing, k.Result]
          .flatMap { promise =>
            promise.succeed(value) *>
              zCache.put(
                RelationRequest[k.Input, Nothing, k.Result](relation, key),
                promise
              )
          }
          .as(zCache)
      }
    }

  // ****** Syntax ************************************

  implicit class RefCacheOps(private val refCache: Ref[Cache]) {

    def add[Rel, A, B](relation: Rel & Relation[A, B], key: A, value: B): UIO[Unit] =
      refCache.update(_.add[Rel, A, B](relation, key, value))

  }
}

object zquery extends zquery[Any]
