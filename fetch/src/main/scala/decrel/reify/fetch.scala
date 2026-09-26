/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import cats.*
import cats.data.NonEmptyList
import cats.effect.{ Clock, Concurrent, Ref }
import cats.implicits.*
import decrel.Relation
import decrel.filter.Predicate
import fetch.*

import scala.collection.immutable.HashMap
import scala.collection.{ mutable, BuildFrom, IterableOps }
import scala.util.control.NoStackTrace

private[decrel] final case class FetchRelationData[Request, Out](
  override val name: String
) extends Data[Request, Out]

/**
 * Instantiate this trait in one place in your app pass around the object, importing it where you want to use it.
 *
 * @tparam F Underlying effect type, usually `cats.effect.IO` or similar.
 */
trait fetch[F[_]] extends catsMonad[Fetch[F, *]] { self =>

  // ****** Implementations for Required Operations **************************

  protected implicit val CF: Concurrent[F]
  override protected implicit lazy val F: Monad[Fetch[F, *]] = _root_.fetch.fetchM[F]

  override protected def foreach[Coll[+T] <: Iterable[T], A, B](
    collection: Coll[A]
  )(
    f: A => Fetch[F, B]
  )(implicit
    bf: BuildFrom[Coll[A], B, Coll[B]]
  ): Fetch[F, Coll[B]] =
    map(Fetch.batchAll(collection.map(f).toSeq*))(_.to(bf.toFactory(collection)))

  // ****** Datasource Implementations ************************************

  private case class RelationRequest[Id, Result](
    relationKey: Any,
    id: Id
  )

  private val datasourceNames = new WeakKeyCache[String]

  private def datasourceName(relationKey: Any): String =
    datasourceNames.getOrCreate(relationKey) {
      DatasourceId.fresh()
    }

  private class FetchDataSourceImpl[In, Out](
    relationKey: Any,
    batchExecute: List[In] => F[List[(In, Out)]]
  ) extends DataSource[F, RelationRequest[In, Out], Out] {
    override val data: Data[RelationRequest[In, Out], Out] =
      new FetchRelationData[RelationRequest[In, Out], Out](datasourceName(relationKey))

    override implicit def CF: Concurrent[F] = self.CF

    override def fetch(id: RelationRequest[In, Out]): F[Option[Out]] =
      batchExecute(List(id.id)).map(r => Some(r.head._2))

    override def batch(
      requests: NonEmptyList[RelationRequest[In, Out]]
    ): F[Map[RelationRequest[In, Out], Out]] = {
      var size = 0
      var list = List.empty[In]

      val requestsList = requests.toList

      requestsList.foreach { request =>
        size += 1
        val newList = request.id :: list
        list = newList
      }

      val requestMapBuilder =
        mutable.HashMap.newBuilder[In, Option[RelationRequest[In, Out]]]
      requestMapBuilder.sizeHint(size)
      requestsList.foreach(request => requestMapBuilder.addOne(request.id -> Some(request)))
      val requestMap = requestMapBuilder.result().withDefaultValue(None)

      val returnsMap = HashMap.newBuilder[RelationRequest[In, Out], Out]
      returnsMap.sizeHint(size)

      batchExecute(list).flatMap { results =>
        var resultSize = 0

        CF.catchOnly[DataSourceImplementationException] {
          results.foreach { pair =>
            val in  = pair._1
            val out = pair._2

            val request = requestMap(in).getOrElse(throw NotRequestedOrDoubleReturns)

            resultSize += 1
            requestMap.update(in, None)
            returnsMap.addOne(request -> out)
          }
        }.flatMap { _ =>
          if (resultSize != size)
            CF.raiseError(NotEnoughReturns)
          else
            CF.pure(returnsMap.result())
        }
      }
    }

    sealed abstract class DataSourceImplementationException(message: String)
        extends RuntimeException(message)
        with NoStackTrace

    private case object NotRequestedOrDoubleReturns
        extends DataSourceImplementationException(
          s"Proof for relation $relationKey has returned data that was either not requested, or returned 2 or more results for the same identifier."
        )

    private case object NotEnoughReturns
        extends DataSourceImplementationException(
          s"Proof for relation $relationKey has not returned enough data"
        )

  }

  // batchExecute is expected to return a List of the same size
  private def buildDatasource[In, Out](relationKey: Any)(
    batchExecute: List[In] => F[List[(In, Out)]]
  ): DataSource[F, RelationRequest[In, Out], Out] =
    new FetchDataSourceImpl[In, Out](relationKey, batchExecute)

  private def singleReifiedRelation[In, Out, Filter <: Predicate[?, ?]](
    relationKey: Any,
    filter: Option[Filter]
  )(
    batchExecute: (List[In], Option[Filter]) => F[List[(In, Out)]]
  ): ReifiedRelation[In, Out] = {
    val ds = buildDatasource[In, Out](relationKey)(ins => batchExecute(ins, filter))

    new ReifiedRelation.Custom[In, Out] {
      override def apply(in: In): Fetch[F, Out] =
        applyMultiple(List(in)).map(_.head)

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        ins: Coll[In]
      ): Fetch[F, Coll[Out]] = {
        val fetches = List.newBuilder[Fetch[F, Out]]
        ins.foreach(in => fetches += Fetch(RelationRequest[In, Out](relationKey, in), ds))

        Fetch
          .batchAll(fetches.result()*)
          .map(_.to((ins: IterableOps[In, Coll, Coll[In]]).iterableFactory))
      }
    }
  }

  // batchExecute must return one entry for every requested input; result order does not matter.
  def implementFilteredSingleDatasource[Rel, In, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: (List[In], Option[Predicate[In, Out]]) => F[List[(In, Option[Out])]]
  ): Proof.Single[Rel & Relation.Single[In, Out], In, Out, Predicate[In, Out]] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, Out, Predicate[In, Out]] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Single[In, Out]) & Relation[In, Out]
      ): ReifiedRelation[In, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, Out] =
        // Required accesses cache Row, matching Cache.add on the unfiltered edge.
        // Filtered accesses below cache Option[Row] under the filtered relation key.
        singleReifiedRelation[In, Out, Predicate[In, Out]](
          filter.fold[Any](relation)(_ => relationKey),
          filter
        ) { (inputs, predicate) =>
          batchExecute(inputs, predicate).map(_.map { case (in, out) =>
            in -> out.getOrElse(
              throw new IllegalStateException(
                s"Single relation implementation returned None for unfiltered access: $relationKey"
              )
            )
          })
        }

      override private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, Option[Out]] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementSingleDatasource[Rel, In, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: (List[In], Option[Nothing]) => F[List[(In, Out)]]
  ): Proof.Single[Rel & Relation.Single[In, Out], In, Out, Nothing] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, Out, Nothing] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Single[In, Out]) & Relation[In, Out]
      ): ReifiedRelation[In, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[In, Out] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementFilteredOptionalDatasource[Rel, In, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: (List[In], Option[Predicate[In, Out]]) => F[List[(In, Option[Out])]]
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out, Predicate[In, Out]] =
    new Proof.Optional[Rel & Relation.Optional[In, Out], In, Out, Predicate[In, Out]] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Optional[In, Out]) & Relation[In, Option[Out]]
      ): ReifiedRelation[In, Option[Out]] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, Option[Out]] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementOptionalDatasource[Rel, In, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: (List[In], Option[Nothing]) => F[List[(In, Option[Out])]]
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out, Nothing] =
    implementFilteredOptionalDatasource[Rel, In, Out](relation)((ins, _) => batchExecute(ins, None))

  def implementFilteredManyDatasource[Rel, In, CC[+A] <: Iterable[
    A
  ] & IterableOps[A, CC, CC[A]], Out](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    batchExecute: (List[In], Option[Predicate[In, Out]]) => F[List[(In, CC[Out])]]
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out, Predicate[In, Out]] =
    new Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out, Predicate[In, Out]] {
      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Many[In, CC, Out]) & Relation[In, CC[Out]]
      ): ReifiedRelation[In, CC[Out]] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, CC[Out]] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def implementManyDatasource[
    Rel,
    In,
    CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
    Out
  ](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    batchExecute: (List[In], Option[Nothing]) => F[List[(In, CC[Out])]]
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out, Nothing] =
    implementFilteredManyDatasource[Rel, In, CC, Out](relation)((ins, _) => batchExecute(ins, None))

  def implementCustomDatasource[Tree, In, Out](
    relation: Relation.Custom[Tree, In, Out]
  )(
    batchExecute: (List[In], Option[Nothing]) => F[List[(In, Out)]]
  ): Proof[Relation.Custom[Tree, In, Out], In, Out, Nothing] =
    new Proof[Relation.Custom[Tree, In, Out], In, Out, Nothing] {
      override private[decrel] def reifyRelation(
        relationValue: Relation.Custom[Tree, In, Out] & Relation[In, Out]
      ): ReifiedRelation[In, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[In, Out] =
        singleReifiedRelation(filter.fold[Any](relation)(_ => relationKey), filter)(batchExecute)
    }

  def contramapOneProof[Rel <: Relation[In, Out], NewRel, In, Out, B, Filter <: Predicate[?, ?]](
    proof: Proof.Single[Rel, In, Out, Filter],
    rel: NewRel & Relation.Single[B, Out],
    f: B => In
  ): Proof.Single[NewRel & Relation.Single[B, Out], B, Out, Nothing] =
    new Proof.Single[NewRel & Relation.Single[B, Out], B, Out, Nothing] {
      override private[decrel] def reifyRelation(
        relation: (NewRel & Relation.Single[B, Out]) & Relation[B, Out]
      ): ReifiedRelation[B, Out] =
        reifyFiltered(relation, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[B, Out] =
        new ReifiedRelation.Custom[B, Out] {
          override def apply(in: B): Fetch[F, Out] =
            proof.reifyFiltered(relationKey, filter).apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[Coll[Out]] =
            proof.reifyFiltered(relationKey, filter).applyMultiple[Coll](in.map(f))
        }

      override private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[B, Option[Out]] =
        new ReifiedRelation.Custom[B, Option[Out]] {
          override def apply(in: B): Fetch[F, Option[Out]] =
            proof.reifyFilteredOptional(relationKey, filter).apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[Coll[Option[Out]]] =
            proof.reifyFilteredOptional(relationKey, filter).applyMultiple[Coll](in.map(f))
        }
    }

  def contramapOptionalProof[
    Rel,
    NewRel,
    In,
    Out,
    B,
    Filter <: Predicate[?, ?]
  ](
    proof: Proof[Rel, In, Out, Filter],
    rel: NewRel & Relation.Optional[B, Out],
    f: B => Option[In]
  ): Proof.Optional[NewRel & Relation.Optional[B, Out], B, Out, Nothing] =
    new Proof.Optional[NewRel & Relation.Optional[B, Out], B, Out, Nothing] {
      override private[decrel] def reifyRelation(
        relation: (NewRel & Relation.Optional[B, Out]) & Relation[B, Option[Out]]
      ): ReifiedRelation[B, Option[Out]] =
        reifyFiltered(relation, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[B, Option[Out]] =
        new ReifiedRelation.Custom[B, Option[Out]] {

          override def apply(in: B): Fetch[F, Option[Out]] =
            proof.reifyFiltered(relationKey, filter).applyMultiple(f(in).toList).map(_.headOption)

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[B]
          ): Access[Coll[Option[Out]]] =
            Fetch
              .batchAll(ins.toList.map(in => apply(in))*)
              .map(_.to((ins: IterableOps[B, Coll, Coll[B]]).iterableFactory))
        }
    }

  def contramapManyProof[
    Rel,
    NewRel,
    In,
    Out,
    B,
    CC[+T] <: Iterable[T] & IterableOps[T, CC, CC[T]],
    Filter <: Predicate[?, ?]
  ](
    proof: Proof[Rel, In, Out, Filter],
    rel: NewRel & Relation.Many[B, CC, Out],
    f: B => CC[In]
  ): Proof.Many[NewRel & Relation.Many[B, CC, Out], B, CC, Out, Nothing] =
    new Proof.Many[NewRel & Relation.Many[B, CC, Out], B, CC, Out, Nothing] {
      override private[decrel] def reifyRelation(
        relation: (NewRel & Relation.Many[B, CC, Out]) & Relation[B, CC[Out]]
      ): ReifiedRelation[B, CC[Out]] =
        reifyFiltered(relation, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[B, CC[Out]] =
        new ReifiedRelation.Custom[B, CC[Out]] {

          override def apply(in: B): Fetch[F, CC[Out]] =
            proof.reifyFiltered(relationKey, filter).applyMultiple(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[B]
          ): Access[Coll[CC[Out]]] =
            Fetch
              .batchAll(ins.toList.map(in => apply(in))*)
              .map(_.to((ins: IterableOps[B, Coll, Coll[B]]).iterableFactory))
        }
    }

  // ****** Cache Implementation ************************************

  implicit class CacheOps(private val cache: Cache) {
    def toFetchDataCache: F[_root_.fetch.DataCache[F]] =
      toFetchCacheImpl(cache)
  }

  private def toFetchCacheImpl(cache: Cache): F[_root_.fetch.DataCache[F]] =
    cache.entries.toList.foldLeftM[F, _root_.fetch.DataCache[F]](
      _root_.fetch.InMemoryCache.empty[F]
    ) { case (acc, (_, v)) =>
      val k: v.key.type                               = v.key
      val relation: k.R & Relation[k.Input, k.Result] = k.relationEv(k._relation)
      val key: k.Input                                = k._key
      val value: k.Result                             = v.valueEv(v._value)

      acc.insert[RelationRequest[k.Input, k.Result], k.Result](
        RelationRequest(relation, key),
        value,
        new FetchRelationData[RelationRequest[k.Input, k.Result], k.Result](
          datasourceName(relation)
        )
      )
    }

  // ****** Syntax ************************************

  /**
   * Syntax for Relation values
   */
  implicit class FetchRelationOps[Rel, In, Out](private val rel: Rel & Relation[In, Out]) {

    def toF(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Out] =
      Fetch.run(toFetch(in))

    def toF(in: In, cache: Cache)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Out] =
      toFetchCacheImpl(cache).flatMap { cache =>
        Fetch.run(toFetch(in), cache)
      }

    def toFMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Coll[Out]] =
      Fetch.run(toFetchMany(in))

    def toFMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Coll[Out]] =
      toFetchCacheImpl(cache).flatMap { cache =>
        Fetch.run(toFetchMany(in), cache)
      }

    def startingFrom(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Out] =
      toF(in)

    def startingFrom(in: In, cache: Cache)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Out] =
      toF(in, cache)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Coll[Out]] =
      toFMany(in)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing],
      clock: Clock[F]
    ): F[Coll[Out]] =
      toFMany(in, cache)

    def toFetch(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing]
    ): Fetch[F, Out] =
      proof.reify(rel).apply(in)

    def toFetchMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing]
    ): Fetch[F, Coll[Out]] =
      proof.reify(rel).applyMultiple(in)

    def startingFromFetch(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing]
    ): Fetch[F, Out] =
      toFetch(in)

    def startingFromFetch[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing]
    ): Fetch[F, Coll[Out]] =
      toFetchMany(in)
  }

  /**
   * Syntax for Relation values
   */
  implicit class FetchReifiedRelationOps[In, Out](
    private val rel: ReifiedRelation[In, Out]
  ) {
    def toF(in: In)(implicit
      clock: Clock[F]
    ): F[Out] =
      Fetch.run(toFetch(in))

    def toF(in: In, cache: Cache)(implicit
      clock: Clock[F]
    ): F[Out] =
      toFetchCacheImpl(cache).flatMap { cache =>
        Fetch.run(toFetch(in), cache)
      }

    def toFMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      clock: Clock[F]
    ): F[Coll[Out]] =
      Fetch.run(toFetchMany(in))

    def toFMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      clock: Clock[F]
    ): F[Coll[Out]] =
      toFetchCacheImpl(cache).flatMap { cache =>
        Fetch.run(toFetchMany(in), cache)
      }

    def startingFrom(in: In)(implicit
      clock: Clock[F]
    ): F[Out] =
      toF(in)

    def startingFrom(in: In, cache: Cache)(implicit
      clock: Clock[F]
    ): F[Out] =
      toF(in, cache)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      clock: Clock[F]
    ): F[Coll[Out]] =
      toFMany(in)

    def startingFrom[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      clock: Clock[F]
    ): F[Coll[Out]] =
      toFMany(in, cache)

    def toFetch(in: In): Fetch[F, Out] =
      rel(in)

    def toFetchMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): Fetch[F, Coll[Out]] =
      rel.applyMultiple(in)

    def startingFromFetch(in: In): Fetch[F, Out] =
      toFetch(in)

    def startingFromFetch[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): Fetch[F, Coll[Out]] =
      toFetchMany(in)
  }

  implicit class RefCacheOps(private val refCache: Ref[F, Cache]) {

    def add[Rel, A, B](relation: Rel & Relation[A, B], key: A, value: B): F[Unit] =
      refCache.update(_.add[Rel, A, B](relation, key, value))

  }
}
