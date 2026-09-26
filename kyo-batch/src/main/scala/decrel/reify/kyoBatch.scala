/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import decrel.filter.Predicate
import kyo.*

import scala.collection.{ BuildFrom, IterableOps }
import scala.collection.IterableFactory

trait kyoBatch[Eff] extends decrel.reify.kyoGeneric[Eff] {

  override final protected def foreach[Coll[+T] <: Iterable[T], A, B](
    collection: Coll[A]
  )(
    f: A => B < Eff
  )(implicit
    bf: BuildFrom[Coll[A], B, Coll[B]]
  ): Coll[B] < Eff =
    Kyo.foreach(collection)(f).flatMap { a =>
      succeed(a.to(bf.toFactory(collection)))
    }

  // ****** Datasource Implementations ************************************

  private[decrel] final class SourceCache[In, Out] {
    private val sources = new WeakKeyCache[In => Out < (Batch & Eff)]

    private[decrel] def getOrCreate(
      relationKey: Any
    )(
      batchExecute: Seq[In] => Map[In, Out] < Eff
    ): In => Out < (Batch & Eff) =
      sources.getOrCreate(relationKey)(Batch.sourceMap[In, Out, Eff](batchExecute))
  }

  private def sourceBackedReifiedRelation[In, Out](
    source: In => Out < (Batch & Eff)
  ): ReifiedRelation[In, Out] =
    new ReifiedRelation.Custom[In, Out] {
      override def apply(in: In): Out < Eff =
        Batch.run(Batch.eval(List(in)).map(source)).map(_.head)

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        ins: Coll[In]
      ): Coll[Out] < Eff = {
        val f: IterableFactory[Coll] =
          (ins: IterableOps[In, Coll, Coll[In]]).iterableFactory

        Batch.run(Batch.eval(ins.toSeq).map(source)).map(a => succeed(a.to(f)))
      }
    }

  private def requiredSingleReifiedRelation[In, Out](
    relationKey: Any,
    relation: ReifiedRelation[In, Option[Out]]
  ): ReifiedRelation[In, Out] =
    new ReifiedRelation.Custom[In, Out] {
      override def apply(in: In): Out < Eff =
        relation.apply(in).map {
          case Some(out) => out
          case None      =>
            throw new IllegalStateException(
              s"Single relation implementation returned None for unfiltered access: $relationKey"
            )
        }

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        ins: Coll[In]
      ): Coll[Out] < Eff =
        relation
          .applyMultiple(ins)
          .map(
            _.map {
              case Some(out) => out
              case None      =>
                throw new IllegalStateException(
                  s"Single relation implementation returned None for unfiltered access: $relationKey"
                )
            }
          )
    }

  def implementFilteredSingleDatasource[Rel, In, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: (Seq[In], Option[Predicate[In, Out]]) => Map[In, Option[Out]] < Eff
  ): Proof.Single[Rel & Relation.Single[In, Out], In, Out, Predicate[In, Out]] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, Out, Predicate[In, Out]] {
      private val sourceCache = new SourceCache[In, Option[Out]]

      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Single[In, Out]) & Relation[In, Out]
      ): ReifiedRelation[In, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, Out] =
        requiredSingleReifiedRelation(
          filter.fold[Any](relation)(_ => relationKey),
          reifyFilteredOptional(relationKey, filter)
        )

      override private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, Option[Out]] =
        sourceBackedReifiedRelation(
          sourceCache
            .getOrCreate(filter.fold[Any](relation)(_ => relationKey))(batchExecute(_, filter))
        )
    }

  def implementSingleDatasource[Rel, In, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: (Seq[In], Option[Nothing]) => Map[In, Out] < Eff
  ): Proof.Single[Rel & Relation.Single[In, Out], In, Out, Nothing] =
    new Proof.Single[Rel & Relation.Single[In, Out], In, Out, Nothing] {
      private val sourceCache = new SourceCache[In, Out]

      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Single[In, Out]) & Relation[In, Out]
      ): ReifiedRelation[In, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[In, Out] =
        sourceBackedReifiedRelation(
          sourceCache
            .getOrCreate(filter.fold[Any](relation)(_ => relationKey))(batchExecute(_, filter))
        )
    }

  def implementFilteredOptionalDatasource[Rel, In, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: (Seq[In], Option[Predicate[In, Out]]) => Map[In, Option[Out]] < Eff
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out, Predicate[In, Out]] =
    new Proof.Optional[Rel & Relation.Optional[In, Out], In, Out, Predicate[In, Out]] {
      private val sourceCache = new SourceCache[In, Option[Out]]

      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Optional[In, Out]) & Relation[In, Option[Out]]
      ): ReifiedRelation[In, Option[Out]] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, Option[Out]] =
        sourceBackedReifiedRelation(
          sourceCache
            .getOrCreate(filter.fold[Any](relation)(_ => relationKey))(batchExecute(_, filter))
        )
    }

  def implementOptionalDatasource[Rel, In, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: (Seq[In], Option[Nothing]) => Map[In, Option[Out]] < Eff
  ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out, Nothing] =
    implementFilteredOptionalDatasource[Rel, In, Out](relation)((ins, _) => batchExecute(ins, None))

  def implementFilteredManyDatasource[Rel, In, CC[+A] <: Iterable[
    A
  ] & IterableOps[A, CC, CC[A]], Out](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    batchExecute: (Seq[In], Option[Predicate[In, Out]]) => Map[In, CC[Out]] < Eff
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out, Predicate[In, Out]] =
    new Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out, Predicate[In, Out]] {
      private val sourceCache = new SourceCache[In, CC[Out]]

      override private[decrel] def reifyRelation(
        relationValue: (Rel & Relation.Many[In, CC, Out]) & Relation[In, CC[Out]]
      ): ReifiedRelation[In, CC[Out]] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Predicate[In, Out]]
      ): ReifiedRelation[In, CC[Out]] =
        sourceBackedReifiedRelation(
          sourceCache
            .getOrCreate(filter.fold[Any](relation)(_ => relationKey))(batchExecute(_, filter))
        )
    }

  def implementManyDatasource[
    Rel,
    In,
    CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
    Out
  ](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    batchExecute: (Seq[In], Option[Nothing]) => Map[In, CC[Out]] < Eff
  ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out, Nothing] =
    implementFilteredManyDatasource[Rel, In, CC, Out](relation)((ins, _) => batchExecute(ins, None))

  def implementCustomDatasource[Tree, In, Out](
    relation: Relation.Custom[Tree, In, Out]
  )(
    batchExecute: (Seq[In], Option[Nothing]) => Map[In, Out] < Eff
  ): Proof[Relation.Custom[Tree, In, Out], In, Out, Nothing] =
    new Proof[Relation.Custom[Tree, In, Out], In, Out, Nothing] {
      private val sourceCache = new SourceCache[In, Out]

      override private[decrel] def reifyRelation(
        relationValue: Relation.Custom[Tree, In, Out] & Relation[In, Out]
      ): ReifiedRelation[In, Out] =
        reifyFiltered(relationValue, None)

      override private[decrel] def reifyFiltered(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[In, Out] =
        sourceBackedReifiedRelation(
          sourceCache
            .getOrCreate(filter.fold[Any](relation)(_ => relationKey))(batchExecute(_, filter))
        )
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
          override def apply(in: B): Out < Eff =
            proof.reifyFiltered(relationKey, filter).apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Coll[Out] < Eff =
            proof.reifyFiltered(relationKey, filter).applyMultiple[Coll](in.map(f))
        }

      override private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Nothing]
      ): ReifiedRelation[B, Option[Out]] =
        new ReifiedRelation.Custom[B, Option[Out]] {
          override def apply(in: B): Option[Out] < Eff =
            proof.reifyFilteredOptional(relationKey, filter).apply(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Coll[Option[Out]] < Eff =
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
          override def apply(in: B): Option[Out] < Eff =
            proof.reifyFiltered(relationKey, filter).applyMultiple(f(in).toList).map(_.headOption)

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Coll[Option[Out]] < Eff = {
            val f = (in: IterableOps[B, Coll, Coll[B]]).iterableFactory
            Kyo.foreach(in)(b => apply(b)).flatMap { a =>
              succeed(a.to(f))
            }
          }
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

          override def apply(in: B): CC[Out] < Eff =
            proof.reifyFiltered(relationKey, filter).applyMultiple(f(in))

          override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[B]
          ): Coll[CC[Out]] < Eff = {
            val collFactory = (in: IterableOps[B, Coll, Coll[B]]).iterableFactory

            Kyo
              .foreach(in)(b => proof.reifyFiltered(relationKey, filter).applyMultiple(f(b)))
              .map(a => succeed(a.to(collFactory)))
          }
        }
    }

  // ****** Cache Implementation ************************************

  implicit class CacheOps(private val cache: Cache) {

    // TODO

  }

  // ****** Syntax ************************************

  implicit class KyoRelationOps[Rel, In, Out](private val rel: Rel & Relation[In, Out]) {

    def startingFrom(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing]
    ): Out < Eff =
      proof.reify(rel).apply(in)

    def startingFromMany[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out, Nothing]
    ): Coll[Out] < Eff =
      proof.reify(rel).applyMultiple(in)

  }

}
