/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.scalacheck

import decrel.Relation
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

    def relationSingle[Rel, In, Out](
      relation: Rel & Relation.Single[In, Out]
    )(
      f: In => Gen[Out]
    ): Proof.Single[Rel & Relation.Single[In, Out], In, Out] =
      new Proof.Single[Rel & Relation.Single[In, Out], In, Out] {
        override val reify: ReifiedRelation[In, Out] = reifiedRelation(f)
      }

    def relationOptional[Rel, In, Out](
      relation: Rel & Relation.Optional[In, Out]
    )(
      f: In => Gen[Option[Out]]
    ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] =
      new Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] {
        override val reify: ReifiedRelation[In, Option[Out]] = reifiedRelation(f)
      }

    def relationMany[Rel, In, Out, CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
      relation: Rel & Relation.Many[In, List, Out]
    )(
      f: In => Gen[CC[Out]]
    ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] =
      new Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] {
        override val reify: ReifiedRelation[In, CC[Out]] = reifiedRelation(f)
      }
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
      proof: Proof[Rel & Relation[A, B], A, B]
    ): Gen[B] = gen.flatMap(rel.reify(proof).apply)

  }
}

object gen extends gen
