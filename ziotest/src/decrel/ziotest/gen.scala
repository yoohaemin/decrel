/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.ziotest

import decrel.Relation
import decrel.reify.monofunctor.*
import zio.*
import zio.test.{ Gen, Sized }

import scala.collection.IterableOps

trait gen[R] extends module[Gen[R, *]] {

  //////// Basic premises

  override protected def flatMap[A, B](gen: Gen[R, A])(f: A => Gen[R, B]): Gen[R, B] =
    gen.flatMap(f)

  override protected def map[A, B](gen: Gen[R, A])(f: A => B): Gen[R, B] =
    gen.map(f)

  override protected def succeed[A](a: A): Gen[R, A] =
    Gen.const(a)

  //////// Syntax for implementing relations

  implicit final class GenObjectOps(private val gen: Gen.type) {

    def relationSingle[Rel, In, Out](
      relation: Rel & Relation.Single[In, Out]
    )(
      f: In => Gen[R, Out]
    ): Proof.Single[Rel & Relation.Single[In, Out], In, Out] =
      new Proof.Single[Rel & Relation.Single[In, Out], In, Out] {
        override val reify: ReifiedRelation[In, Out] = reifiedRelation(f)
      }

    def relationOptional[Rel, In, Out](
      relation: Rel & Relation.Optional[In, Out]
    )(
      f: In => Gen[R, Option[Out]]
    ): Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] =
      new Proof.Optional[Rel & Relation.Optional[In, Out], In, Out] {
        override val reify: ReifiedRelation[In, Option[Out]] = reifiedRelation(f)
      }

    def relationMany[Rel, In, Out, CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
      relation: Rel & Relation.Many[In, List, Out]
    )(
      f: In => Gen[R, CC[Out]]
    ): Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] =
      new Proof.Many[Rel & Relation.Many[In, CC, Out], In, CC, Out] {
        override val reify: ReifiedRelation[In, CC[Out]] = reifiedRelation(f)
      }
  }

  private def reifiedRelation[In, Out](f: In => Gen[R, Out]): ReifiedRelation[In, Out] =
    new ReifiedRelation.Custom[In, Out] {
      override def apply(in: In): Gen[R, Out] =
        applyMultiple(List(in)).map(_.head)

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        ins: Coll[In]
      ): Gen[R, Coll[Out]] =
        Gen
          .collectAll(ins.map(f))
          .map(_.to((ins: IterableOps[In, Coll, Coll[In]]).iterableFactory))
    }

  //////// Syntax for using relations in tests

  implicit final class GenOps[A](private val gen: Gen[R, A]) {

    def expand[Rel, B](rel: Rel & Relation[A, B])(implicit
      proof: Proof[Rel & Relation[A, B], A, B]
    ): Gen[R, B] = gen.flatMap(rel.reify(proof).apply)

  }
}

object gen extends gen[Any] {

  object randomWithSized extends gen[Random & Sized]

}
