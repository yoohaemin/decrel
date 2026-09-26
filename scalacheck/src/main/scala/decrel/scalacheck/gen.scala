/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.scalacheck

import decrel.Relation
import decrel.filter.Predicate
import decrel.reify.monofunctor.*
import org.scalacheck.Gen
import org.scalacheck.util.Buildable

import scala.collection.IterableOps

trait gen extends module[Gen] {

  //////// Basic premises

  override protected def flatMap[A, B](gen: Gen[A])(f: A => Gen[B]): Gen[B] =
    gen.flatMap(f)

  override protected def map[A, B](gen: Gen[A])(f: A => B): Gen[B] =
    gen.map(f)

  override protected def succeed[A](a: A): Gen[A] =
    Gen.const(a)

  //////// Syntax for implementing relations

  implicit final class GenObjectOps(private val gen: Gen.type) {

    def filteredRelationSingle[Rel, In, Out](
      relation: Rel & Relation.Single[In, Out]
    )(
      f: (In, Option[Predicate[In, Out]]) => Gen[Option[Out]]
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
          new ReifiedRelation.Custom[In, Out] {
            override def apply(in: In): Gen[Out] =
              reifyFilteredOptional(relationKey, filter).apply(in).map {
                case Some(out) => out
                case None      =>
                  throw new IllegalStateException(
                    s"Single relation implementation returned None for unfiltered access: $relationKey"
                  )
              }

            override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
              ins: Coll[In]
            ): Gen[Coll[Out]] =
              reifyFilteredOptional(relationKey, filter)
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

        override private[decrel] def reifyFilteredOptional(
          relationKey: Any,
          filter: Option[Predicate[In, Out]]
        ): ReifiedRelation[In, Option[Out]] =
          reifiedRelation(f(_, filter))
      }

    def relationSingle[Rel, In, Out](
      relation: Rel & Relation.Single[In, Out]
    )(
      f: (In, Option[Nothing]) => Gen[Out]
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
          reifiedRelation(f(_, filter))
      }

    def filteredRelationOptional[Rel, In, Out](
      relation: Rel & Relation.Optional[In, Out]
    )(
      f: (In, Option[Predicate[In, Out]]) => Gen[Option[Out]]
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
          reifiedRelation(f(_, filter))
      }

    def relationOptional[Rel, In, Out](
      relation: Rel & Relation.Optional[In, Out]
    )(
      f: (In, Option[Nothing]) => Gen[Option[Out]]
    ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out, Nothing] =
      filteredRelationOptional[Rel, In, Out](relation)((in, _) => f(in, None))

    def filteredRelationMany[Rel, In, Out, CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
      relation: Rel & Relation.Many[In, CC, Out]
    )(
      f: (In, Option[Predicate[In, Out]]) => Gen[CC[Out]]
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
          reifiedRelation(f(_, filter))
      }

    def relationMany[
      Rel,
      In,
      Out,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](
      relation: Rel & Relation.Many[In, CC, Out]
    )(
      f: (In, Option[Nothing]) => Gen[CC[Out]]
    ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out, Nothing] =
      filteredRelationMany[Rel, In, Out, CC](relation)((in, _) => f(in, None))
  }

  private def reifiedRelation[In, Out](f: In => Gen[Out]): ReifiedRelation[In, Out] =
    new ReifiedRelation.Custom[In, Out] {
      override def apply(in: In): Gen[Out] =
        applyMultiple(List(in)).map(_.head)

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        ins: Coll[In]
      ): Gen[Coll[Out]] =
        Gen
          .sequence(ins.map(f))(Buildable.buildableSeq)
          .map(_.to((ins: IterableOps[In, Coll, Coll[In]]).iterableFactory))
    }

  //////// Syntax for using relations in tests

  implicit final class GenOps[A](private val gen: Gen[A]) {

    def expand[Rel, B](rel: Rel & Relation[A, B])(implicit
      proof: Proof[Rel & Relation[A, B], A, B, Nothing]
    ): Gen[B] = gen.flatMap(proof.reify(rel).apply)

  }
}

object gen extends gen
