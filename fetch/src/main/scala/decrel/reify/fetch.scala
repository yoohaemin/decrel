/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import cats.*
import cats.implicits.*
import cats.data.NonEmptyList
import cats.effect.{ Clock, Concurrent, Ref }
import decrel.Relation
import fetch.*
import izumi.reflect.Tag

import scala.collection.immutable.HashMap
import scala.collection.{ mutable, BuildFrom, IterableOps }
import scala.util.control.NoStackTrace

/**
 * Instantiate this trait in one place in your app pass around the object, importing it where you want to use it.
 *
 * @tparam F Underlying effect type, usually `cats.effect.IO` or similar.
 */
trait fetch[F[_]] extends catsMonad[Fetch[F, *]] { self =>

  // ****** Implementations for Required Operations **************************

  protected implicit val CF: Concurrent[F]
  override protected implicit val F: Monad[Fetch[F, *]] = fetch.fetchM[F]

  override protected def foreach[Coll[+T] <: Iterable[T], A, B](
    collection: Coll[A]
  )(
    f: A => Fetch[F, B]
  )(implicit
    bf: BuildFrom[Coll[A], B, Coll[B]]
  ): Fetch[F, Coll[B]] =
    map(Fetch.batchAll(collection.map(f).toSeq*))(_.to(bf.toFactory(collection)))

  // ****** Datasource Implementations ************************************

  private case class RelationRequest[Rel, Id, Result](
    rel: Rel & Relation[Id, Result],
    id: Id
  )

  private class RelationRequestData[Rel, In, Out](
    val rel: Rel,
    override val name: String
  ) extends Data[RelationRequest[Rel, In, Out], Out] {

    def this(rel: Rel, tag: Tag[Rel]) =
      this(rel, "RelationDatasource:" + tag.tag.longNameWithPrefix)
  }

  private class FetchDataSourceImpl[Rel: Tag, In, Out](
    rel: Rel,
    batchExecute: List[In] => F[List[(In, Out)]]
  ) extends DataSource[F, RelationRequest[Rel, In, Out], Out] {
    override def data: Data[RelationRequest[Rel, In, Out], Out] =
      new RelationRequestData[Rel, In, Out](rel, Tag[Rel])

    override implicit def CF: Concurrent[F] = self.CF

    override def fetch(id: RelationRequest[Rel, In, Out]): F[Option[Out]] =
      batchExecute(List(id.id)).map(r => Some(r.head._2))

    override def batch(
      requests: NonEmptyList[RelationRequest[Rel, In, Out]]
    ): F[Map[RelationRequest[Rel, In, Out], Out]] = {
      // Size of the request. Needs to be checked against the result.
      var size = 0

      // List that will be fed into the provided implementation function.
      var list = List.empty[In]

      // Saves 1 allocation because this is referenced 2 times
      val requestsList = requests.toList

      // Build the request list and size in one traversal
      requestsList.foreach { request =>
        size += 1
        val newList = request.id :: list
        list = newList
      }

      // Create a table from `In`s to `RelationRequest`s for fast lookup
      val requestMapBuilder =
        mutable.HashMap.newBuilder[In, Option[RelationRequest[Rel, In, Out]]]
      requestMapBuilder.sizeHint(size)
      requestsList.foreach(request => requestMapBuilder.addOne(request.id -> Some(request)))
      val requestMap = requestMapBuilder.result().withDefaultValue(None)

      // Create a builder that will ultimately be returned
      val returnsMap = HashMap.newBuilder[RelationRequest[Rel, In, Out], Out]
      returnsMap.sizeHint(size)

      batchExecute(list).flatMap { results =>
        // to compare against the request size
        var resultSize = 0

        CF.catchOnly[DataSourceImplementationException] {
          results.foreach { pair =>
            // Blindly avoiding pattern matching
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
          s"Proof for relation $rel has returned data that was either not requested, or returned 2 or more results for the same identifier."
        )

    private case object NotEnoughReturns
        extends DataSourceImplementationException(
          s"Proof for relation $rel has not returned enough data"
        )

  }

  // batchExecute is expected to return a List of the same size
  private def buildDatasource[Rel: Tag, In, Out](rel: Rel)(
    batchExecute: List[In] => F[List[(In, Out)]]
  ): DataSource[F, RelationRequest[Rel, In, Out], Out] =
    new FetchDataSourceImpl[Rel, In, Out](rel, batchExecute)

  def implementSingleDatasource[Rel: Tag, In, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: List[In] => F[List[(In, Out)]]
  ): Proof.Single[Rel & Relation.Single[In, Out], In, Out] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, Out] {

      private val ds = buildDatasource[Rel, In, Out](relation)(batchExecute)

      override val reify: ReifiedRelation[In, Out] =
        new ReifiedRelation.Custom[In, Out] {
          override def apply(in: In): Fetch[F, Out] =
            applyMultiple(List(in)).map(_.head) // TODO add tests for this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Fetch[F, Coll[Out]] = {
            val fetches = List.newBuilder[Fetch[F, Out]]
            ins.foreach(in => fetches += Fetch(RelationRequest[Rel, In, Out](relation, in), ds))

            Fetch
              .batchAll(fetches.result()*)
              .map(_.to((ins: IterableOps[In, Coll, Coll[In]]).iterableFactory))
          }
        }
    }

  def implementOptionalDatasource[Rel: Tag, In, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: List[In] => F[List[(In, Option[Out])]]
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] =
    new Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] {

      private val ds = buildDatasource[Rel, In, Option[Out]](relation)(batchExecute)

      override val reify: ReifiedRelation[In, Option[Out]] =
        new ReifiedRelation.Custom[In, Option[Out]] {
          override def apply(in: In): Fetch[F, Option[Out]] =
            applyMultiple(List(in)).map(_.head) // TODO This should be safe... let's test this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Fetch[F, Coll[Option[Out]]] = {
            val fetches = List.newBuilder[Fetch[F, Option[Out]]]
            ins.foreach(in =>
              fetches += Fetch(RelationRequest[Rel, In, Option[Out]](relation, in), ds)
            )

            Fetch
              .batchAll(fetches.result()*)
              .map(_.to((ins: IterableOps[In, Coll, Coll[In]]).iterableFactory))
          }
        }
    }

  def implementManyDatasource[
    Rel: Tag,
    In,
    CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
    Out
  ](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: List[In] => F[List[(In, CC[Out])]]
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] =
    new Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] {

      private val ds = buildDatasource[Rel, In, CC[Out]](relation)(batchExecute)

      override val reify: ReifiedRelation[In, CC[Out]] =
        new ReifiedRelation.Custom[In, CC[Out]] {
          override def apply(in: In): Fetch[F, CC[Out]] =
            applyMultiple(List(in)).map(_.head) // TODO This should be safe... let's test this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Fetch[F, Coll[CC[Out]]] = {
            val fetches = List.newBuilder[Fetch[F, CC[Out]]]
            ins.foreach(in => fetches += Fetch(RelationRequest[Rel, In, CC[Out]](relation, in), ds))

            Fetch
              .batchAll(fetches.result()*)
              .map(_.to((ins: IterableOps[In, Coll, Coll[In]]).iterableFactory))
          }
        }
    }

  def implementCustomDatasource[
    Tree,
    In,
    Out
  ](
    relation: Relation.Custom[Tree, In, Out]
  )(
    // Also should allow exception per request, so when failing we can give back what we fetched so far
    batchExecute: List[In] => F[List[(In, Out)]]
  )(implicit
    tag: Tag[Relation.Custom[Tree, In, Out]]
  ): Proof[Relation.Custom[Tree, In, Out], In, Out] =
    new Proof[Relation.Custom[Tree, In, Out], In, Out] {
      private val ds = buildDatasource(relation)(batchExecute)

      type Rel = Relation.Custom[Tree, In, Out]

      override val reify: ReifiedRelation[In, Out] =
        new ReifiedRelation.Custom[In, Out] {
          override def apply(in: In): Fetch[F, Out] =
            applyMultiple(List(in)).map(_.head) // TODO This should be safe... let's test this

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Fetch[F, Coll[Out]] = {
            val fetches = List.newBuilder[Fetch[F, Out]]
            ins.foreach(in => fetches += Fetch(RelationRequest[Rel, In, Out](relation, in), ds))

            Fetch
              .batchAll(fetches.result()*)
              .map(_.to((ins: IterableOps[In, Coll, Coll[In]]).iterableFactory))
          }
        }
    }

  def contramapOneProof[Rel, NewRel, In, Out, B](
    proof: Proof[Rel, In, Out],
    rel: NewRel & Relation.Single[B, Out],
    f: B => In
  ): Proof.Single[NewRel & Relation.Single[B, Out], B, Out] =
    new Proof.Single[NewRel & Relation.Single[B, Out], B, Out] {
      override val reify: ReifiedRelation[B, Out] =
        new ReifiedRelation.Custom[B, Out] {
          override def apply(in: B): Fetch[F, Out] =
            proof.reify.apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[Coll[Out]] =
            proof.reify.applyMultiple[Coll](in.map(f))
        }
    }

  def contramapOptionalProof[
    Rel,
    NewRel,
    In,
    Out,
    B
  ](
    proof: Proof[Rel, In, Out],
    rel: NewRel & Relation.Optional[B, Out],
    f: B => Option[In]
  ): Proof.Optional[NewRel & Relation.Optional[B, Out], B, Out] =
    new Proof.Optional[NewRel & Relation.Optional[B, Out], B, Out] {
      override val reify: ReifiedRelation[B, Option[Out]] =
        new ReifiedRelation.Custom[B, Option[Out]] {

          override def apply(in: B): Fetch[F, Option[Out]] =
            proof.reify.applyMultiple(f(in).toList).map(_.headOption)

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[B]
          ): Access[Coll[Option[Out]]] =
            // TODO test if batching support is proper
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
    CC[+T] <: Iterable[T] & IterableOps[T, CC, CC[T]]
  ](
    proof: Proof[Rel, In, Out],
    rel: NewRel & Relation.Many[B, CC, Out],
    f: B => CC[In]
  ): Proof.Many[NewRel & Relation.Many[B, CC, Out], B, CC, Out] =
    new Proof.Many[NewRel & Relation.Many[B, CC, Out], B, CC, Out] {
      override val reify: ReifiedRelation[B, CC[Out]] =
        new ReifiedRelation.Custom[B, CC[Out]] {

          override def apply(in: B): Fetch[F, CC[Out]] =
            proof.reify.applyMultiple(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[B]
          ): Access[Coll[CC[Out]]] =
            // TODO test if batching support is proper
            Fetch
              .batchAll(ins.toList.map(in => apply(in))*)
              .map(_.to((ins: IterableOps[B, Coll, Coll[B]]).iterableFactory))
        }
    }

  // ****** Cache Implementation ************************************

  implicit class CacheOps(private val cache: Cache) {
    def toFetchDataCache: F[fetch.DataCache[F]] =
      toFetchCacheImpl(cache)
  }

  private def toFetchCacheImpl(cache: Cache): F[fetch.DataCache[F]] =
    cache.entries.toList.foldLeftM[F, fetch.DataCache[F]](fetch.InMemoryCache.empty[F]) {
      case (acc, (_, v)) =>
        val k: v.key.type                               = v.key
        val tag: Tag[k.R]                               = k.tag
        val relation: k.R & Relation[k.Input, k.Result] = k.relationEv(k._relation)
        val key: k.Input                                = k._key
        val value: k.Result                             = v.valueEv(v._value)

        acc.insert[RelationRequest[k.R, k.Input, k.Result], k.Result](
          RelationRequest(relation, key),
          value,
          new RelationRequestData(relation, tag)
        )
    }

  // ****** Syntax ************************************

  /**
   * Syntax for Relation values
   */
  implicit class FetchRelationOps[Rel, In, Out](private val rel: Rel & Relation[In, Out]) { // TODO add AnyVal
    def startingFrom(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out],
      clock: Clock[F]
    ): F[Out] =
      Fetch.run(startingFromFetch(in))

    def startingFrom(in: In, cache: Cache)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out],
      clock: Clock[F]
    ): F[Out] =
      toFetchCacheImpl(cache).flatMap { cache =>
        Fetch.run(startingFromFetch(in), cache)
      }

    def startingFromMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out],
      clock: Clock[F]
    ): F[Coll[Out]] =
      Fetch.run(startingFromManyFetch(in))

    def startingFromMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In],
      cache: Cache
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out],
      clock: Clock[F]
    ): F[Coll[Out]] =
      toFetchCacheImpl(cache).flatMap { cache =>
        Fetch.run(startingFromManyFetch(in), cache)
      }

    def startingFromFetch(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out]
    ): Fetch[F, Out] =
      proof.reify(in)

    def startingFromManyFetch[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out]
    ): Fetch[F, Coll[Out]] =
      proof.reify.applyMultiple(in)
  }

  implicit class RefCacheOps(private val refCache: Ref[F, Cache]) {

    def add[Rel, A, B](relation: Rel & Relation[A, B], key: A, value: B)(implicit
      tag: Tag[Rel]
    ): F[Unit] =
      refCache.update(_.add[Rel, A, B](relation, key, value))

  }

}
