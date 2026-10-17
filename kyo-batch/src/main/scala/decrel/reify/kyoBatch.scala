/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import izumi.reflect.Tag
import kyo.*

import scala.collection.{ BuildFrom, IterableOps }
import scala.collection.IterableFactory

trait kyoBatch[Eff] extends monofunctor.module[[A] =>> KyoBatchQuery[Eff, A]] {

  override protected def succeed[A](value: A): Access[A] = KyoBatchQuery.pure(value)
  override protected def map[A, B](value: Access[A])(f: A => B): Access[B] = value.map(f)
  override protected def flatMap[A, B](value: Access[A])(f: A => Access[B]): Access[B] =
    value.flatMap(f)

  override protected def zipWith[A, B, C](left: => Access[A], right: => Access[B])(
    f: (A, B) => C
  ): Access[C] =
    KyoBatchQuery.zipWith(left, right)(f)

  override protected def foreach[Coll[+T] <: Iterable[T], A, B](collection: Coll[A])(
    f: A => Access[B]
  )(implicit bf: BuildFrom[Coll[A], B, Coll[B]]): Access[Coll[B]] =
    KyoBatchQuery.traverse(collection.toVector)(f).map(_.to(bf.toFactory(collection)))

  // ****** Datasource Implementations ************************************

  def implementSingleDatasource[Rel: Tag, In, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: Seq[In] => Map[In, Out] < Eff
  )(implicit
    tag: Tag[Out]
  ): Proof.Single[Rel & Relation.Single[In, Out], In, Out] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, Out] {

      private val source = Batch.sourceMap[In, Out, Eff](batchExecute)

      override val reify: ReifiedRelation[In, Out] =
        new ReifiedRelation.Custom[In, Out] {
          override def apply(in: In): Access[Out] =
            KyoBatchQuery.request(source(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Access[Coll[Out]] = {
            val f: IterableFactory[Coll] =
              (ins: IterableOps[In, Coll, Coll[In]]).iterableFactory

            KyoBatchQuery
              .traverse(ins.toVector)(in => KyoBatchQuery.request(source(in)))
              .map(_.to(f))
          }

        }
    }

  def implementOptionalDatasource[Rel: Tag, In, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: Seq[In] => Map[In, Option[Out]] < Eff
  )(implicit
    tag: Tag[Out]
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] =
    new Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] {

      private val ds = Batch.sourceMap[In, Option[Out], Eff](batchExecute)

      override val reify: ReifiedRelation[In, Option[Out]] =
        new ReifiedRelation.Custom[In, Option[Out]] {
          override def apply(in: In): Access[Option[Out]] =
            KyoBatchQuery.request(ds(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Access[Coll[Option[Out]]] = {
            val f: IterableFactory[Coll] =
              (ins: IterableOps[In, Coll, Coll[In]]).iterableFactory

            KyoBatchQuery.traverse(ins.toVector)(in => KyoBatchQuery.request(ds(in))).map(_.to(f))
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
    batchExecute: Seq[In] => Map[In, CC[Out]] < Eff
  )(implicit
    tag: Tag[CC[Out]]
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] =
    new Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] {

      private val ds = Batch.sourceMap[In, CC[Out], Eff](batchExecute)

      override val reify: ReifiedRelation[In, CC[Out]] =
        new ReifiedRelation.Custom[In, CC[Out]] {
          override def apply(in: In): Access[CC[Out]] =
            KyoBatchQuery.request(ds(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Access[Coll[CC[Out]]] = {
            val f: IterableFactory[Coll] =
              (ins: IterableOps[In, Coll, Coll[In]]).iterableFactory

            KyoBatchQuery.traverse(ins.toVector)(in => KyoBatchQuery.request(ds(in))).map(_.to(f))
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
    batchExecute: Seq[In] => Map[In, Out] < Eff
  )(implicit
    tag: Tag[Relation.Custom[Tree, In, Out]]
  ): Proof[Relation.Custom[Tree, In, Out], In, Out] =
    new Proof[Relation.Custom[Tree, In, Out], In, Out] {
      private val ds = Batch.sourceMap[In, Out, Eff](batchExecute)

      override val reify: ReifiedRelation[In, Out] =
        new ReifiedRelation.Custom[In, Out] {
          override def apply(in: In): Access[Out] =
            KyoBatchQuery.request(ds(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            ins: Coll[In]
          ): Access[Coll[Out]] = {
            val f: IterableFactory[Coll] =
              (ins: IterableOps[In, Coll, Coll[In]]).iterableFactory

            KyoBatchQuery.traverse(ins.toVector)(in => KyoBatchQuery.request(ds(in))).map(_.to(f))
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
          override def apply(in: B): Access[Out] =
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
          override def apply(in: B): Access[Option[Out]] =
            proof.reify.applyMultiple(f(in).toList).map(_.headOption)

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[Coll[Option[Out]]] = {
            val f = (in: IterableOps[B, Coll, Coll[B]]).iterableFactory
            KyoBatchQuery.traverse(in.toVector)(b => apply(b)).map(_.to(f))
          }

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

          override def apply(in: B): Access[CC[Out]] =
            proof.reify.applyMultiple(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Access[Coll[CC[Out]]] = {
            val collFactory = (in: IterableOps[B, Coll, Coll[B]]).iterableFactory

            KyoBatchQuery
              .traverse(in.toVector)(b => proof.reify.applyMultiple(f(b)))
              .map(_.to(collFactory))
          }
        }
    }

  // ****** Cache Implementation ************************************

  implicit class CacheOps(private val cache: Cache) {

    // TODO

  }

  // ****** Syntax ************************************

  /**
   * Syntax for Relation values
   */
  implicit class KyoRelationOps[Rel, In, Out](private val rel: Rel & Relation[In, Out]) { // TODO add AnyVal

    def startingFrom(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out]
    ): Out < Eff =
      proof.reify.apply(in).run

    def startingFromMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out]
    ): Coll[Out] < Eff =
      proof.reify.applyMultiple(in).run

  }

}
