/*
 * Copyright (c) Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import scala.collection.IterableOps
import scala.language.implicitConversions
import zio.test.*

object ProofConstructionSpec extends ZIOSpecDefault {
  final case class Offset(amount: Int)         extends Relation.Single[Int, Int]
  final case class OptionalOffset(amount: Int) extends Relation.Optional[Int, Int]
  final case class ManyOffset(amount: Int)     extends Relation.Many[Int, Vector, Int]

  class Binary extends either {
    val constructed = scala.collection.mutable.ArrayBuffer.empty[Int]

    implicit def offsetProof(value: Offset): Proof.Single[Offset, Int, Nothing, Int] = {
      constructed += value.amount
      val amount = value.amount
      new Proof.Single[Offset, Int, Nothing, Int] {
        override val reify: ReifiedRelation[Int, Nothing, Int] =
          new ReifiedRelation.Custom[Int, Nothing, Int] {
            override def apply(in: Int): Either[Nothing, Int] = Right(in + amount)
            override def applyMultiple[CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
              in: CC[Int]
            ): Either[Nothing, CC[Int]] = Right(in.map(_ + amount))
          }
      }
    }

    implicit def optionalOffsetProof(
      value: OptionalOffset
    ): Proof.Optional[OptionalOffset, Int, Nothing, Int] =
      offsetProof(Offset(value.amount)).contramapOptional(value)(in => Option.when(in >= 0)(in))

    implicit def manyOffsetProof(
      value: ManyOffset
    ): Proof.Many[ManyOffset, Int, Nothing, Vector, Int] =
      offsetProof(Offset(value.amount)).contramapMany[ManyOffset, Int, Vector](value)(in =>
        Vector(in, in + 1)
      )
  }

  object binary extends Binary

  object unary extends monofunctor.module[Option] {
    override protected def flatMap[A, B](a: Option[A])(f: A => Option[B]): Option[B] = a.flatMap(f)
    override protected def map[A, B](a: Option[A])(f: A => B): Option[B]             = a.map(f)
    override protected def succeed[A](a: A): Option[A]                               = Some(a)

    implicit def offsetProof(value: Offset): Proof.Single[Offset, Int, Int] = {
      val amount = value.amount
      new Proof.Single[Offset, Int, Int] {
        override val reify: ReifiedRelation[Int, Int] =
          new ReifiedRelation.Custom[Int, Int] {
            override def apply(in: Int): Option[Int] = Some(in + amount)
            override def applyMultiple[CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
              in: CC[Int]
            ): Option[CC[Int]] = Some(in.map(_ + amount))
          }
      }
    }

    implicit def optionalOffsetProof(
      value: OptionalOffset
    ): Proof.Optional[OptionalOffset, Int, Int] =
      offsetProof(Offset(value.amount)).contramapOptional(value)(in => Option.when(in >= 0)(in))

    implicit def manyOffsetProof(value: ManyOffset): Proof.Many[ManyOffset, Int, Vector, Int] =
      offsetProof(Offset(value.amount)).contramapMany[ManyOffset, Int, Vector](value)(in =>
        Vector(in, in + 1)
      )
  }

  override def spec: Spec[Environment, Any] = suite("proof construction")(
    test("bifunctor proof captures the relation while constructing the implicit") {
      import binary.*
      val first  = Offset(1)
      val second = Offset(10)
      val proof  = relationOps(first).proof
      assertTrue(
        proof.reify eq proof.reify,
        proof.reify(2) == Right(3),
        second.reify.apply(2) == Right(12),
        (first >>: second).reify.apply(2) == Right(13),
        first.zip(second).reify.apply(2) == Right((3, 12)),
        (first >>: (second >>: first)).reify.apply(2) == Right(14),
        (OptionalOffset(1) >>: second).reify.apply(2) == Right(Some(13)),
        (OptionalOffset(1) >>: second).reify.apply(-1) == Right(None),
        (ManyOffset(1) >>: second).reify.apply(2) == Right(Vector(13, 14)),
        OptionalOffset(1).zip(ManyOffset(10)).reify.apply(2) == Right((Some(3), Vector(12, 13))),
        (Relation.Self[Int] >>: first).reify.apply(2) == Right(3)
      )
    },
    test("monofunctor proof captures the relation while constructing the implicit") {
      import unary.*
      val first  = Offset(1)
      val second = Offset(10)
      val proof  = relationOps(first).proof
      assertTrue(
        proof.reify eq proof.reify,
        proof.reify(2) == Some(3),
        second.reify.apply(2) == Some(12),
        (first >>: second).reify.apply(2) == Some(13),
        first.zip(second).reify.apply(2) == Some((3, 12)),
        (first >>: (second >>: first)).reify.apply(2) == Some(14),
        (OptionalOffset(1) >>: second).reify.apply(2) == Some(Some(13)),
        (OptionalOffset(1) >>: second).reify.apply(-1) == Some(None),
        (ManyOffset(1) >>: second).reify.apply(2) == Some(Vector(13, 14)),
        OptionalOffset(1).zip(ManyOffset(10)).reify.apply(2) == Some((Some(3), Vector(12, 13))),
        (Relation.Self[Int] >>: first).reify.apply(2) == Some(3)
      )
    },
    test("syntax constructs child proofs before reify and does not rebuild them") {
      val module = new Binary
      import module.*
      val relation    = Offset(1) >>: (Offset(10) >>: Offset(100))
      val proof       = relationOps(relation).proof
      val beforeReify = constructed.toVector
      val interpreter = proof.reify
      assertTrue(
        beforeReify == Vector(1, 10, 100),
        interpreter eq proof.reify,
        interpreter(2) == Right(113),
        constructed.toVector == beforeReify
      )
    }
  )
}
