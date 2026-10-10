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

    implicit val offsetProof: Proof.Single[Offset, Int, Nothing, Int] =
      new Proof.Single[Offset, Int, Nothing, Int] {
        override def apply(value: Offset): ReifiedRelation[Int, Nothing, Int] = {
          constructed += value.amount
          new ReifiedRelation.Custom[Int, Nothing, Int] {
            override def apply(in: Int): Either[Nothing, Int] = Right(in + value.amount)
            override def applyMultiple[CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
              in: CC[Int]
            ): Either[Nothing, CC[Int]] = Right(in.map(_ + value.amount))
          }
        }
      }

    implicit val optionalOffsetProof: Proof.Optional[OptionalOffset, Int, Nothing, Int] =
      new Proof.Optional[OptionalOffset, Int, Nothing, Int] {
        override def apply(value: OptionalOffset): ReifiedRelation[Int, Nothing, Option[Int]] =
          offsetProof.contramapOptional(Offset(value.amount), value)((in: Int) =>
            Option.when(in >= 0)(in)
          )(value)
      }

    implicit val manyOffsetProof: Proof.Many[ManyOffset, Int, Nothing, Vector, Int] =
      new Proof.Many[ManyOffset, Int, Nothing, Vector, Int] {
        override def apply(value: ManyOffset): ReifiedRelation[Int, Nothing, Vector[Int]] =
          offsetProof.contramapMany[ManyOffset, Int, Vector](Offset(value.amount), value)(in =>
            Vector(in, in + 1)
          )(implicitly)(value)
      }
  }

  object binary extends Binary

  object unary extends monofunctor.module[Option] {
    override protected def flatMap[A, B](a: Option[A])(f: A => Option[B]): Option[B] = a.flatMap(f)
    override protected def map[A, B](a: Option[A])(f: A => B): Option[B]             = a.map(f)
    override protected def succeed[A](a: A): Option[A]                               = Some(a)

    implicit val offsetProof: Proof.Single[Offset, Int, Int] =
      new Proof.Single[Offset, Int, Int] {
        override def apply(value: Offset): ReifiedRelation[Int, Int] =
          new ReifiedRelation.Custom[Int, Int] {
            override def apply(in: Int): Option[Int] = Some(in + value.amount)
            override def applyMultiple[CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
              in: CC[Int]
            ): Option[CC[Int]] = Some(in.map(_ + value.amount))
          }
      }

    implicit val optionalOffsetProof: Proof.Optional[OptionalOffset, Int, Int] =
      new Proof.Optional[OptionalOffset, Int, Int] {
        override def apply(value: OptionalOffset): ReifiedRelation[Int, Option[Int]] =
          offsetProof.contramapOptional(Offset(value.amount), value)((in: Int) =>
            Option.when(in >= 0)(in)
          )(value)
      }

    implicit val manyOffsetProof: Proof.Many[ManyOffset, Int, Vector, Int] =
      new Proof.Many[ManyOffset, Int, Vector, Int] {
        override def apply(value: ManyOffset): ReifiedRelation[Int, Vector[Int]] =
          offsetProof.contramapMany[ManyOffset, Int, Vector](Offset(value.amount), value)(in =>
            Vector(in, in + 1)
          )(implicitly)(value)
      }
  }

  override def spec: Spec[Environment, Any] = suite("proof construction")(
    test("bifunctor proof binds the actual relation value") {
      import binary.*
      val first  = Offset(1)
      val second = Offset(10)
      val ops    = relationOps(first)
      assertTrue(
        ops.reify eq ops.reify,
        ops.reify(2) == Right(3),
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
    test("monofunctor proof binds the actual relation value") {
      import unary.*
      val first  = Offset(1)
      val second = Offset(10)
      val ops    = relationOps(first)
      assertTrue(
        ops.reify eq ops.reify,
        ops.reify(2) == Some(3),
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
    test("one bifunctor proof constructs independent interpreters for different relation trees") {
      val module = new Binary
      import module.*
      type Tree = Relation.Composed.Single[Offset, Int, Int, Offset, Int, Int]
      val proof  = implicitly[Proof[Tree, Int, Nothing, Int]]
      val before = constructed.toVector
      val first  = proof(Offset(1) >>: Offset(10))
      val second = proof(Offset(100) >>: Offset(1000))
      assertTrue(
        before.isEmpty,
        first(2) == Right(13),
        second(2) == Right(1102),
        first.applyMultiple(Vector(2, 3)) == Right(Vector(13, 14)),
        constructed.toVector == Vector(1, 10, 100, 1000)
      )
    },
    test("one monofunctor proof constructs independent interpreters for different relation trees") {
      import unary.*
      type Tree = Relation.Composed.Single[Offset, Int, Int, Offset, Int, Int]
      val proof  = implicitly[Proof[Tree, Int, Int]]
      val first  = proof(Offset(1) >>: Offset(10))
      val second = proof(Offset(100) >>: Offset(1000))
      assertTrue(
        first(2) == Some(13),
        second(2) == Some(1102),
        first.applyMultiple(Vector(2, 3)) == Some(Vector(13, 14))
      )
    },
    test("contramap binds the supplied source once when registering the target proof") {
      val module = new Binary
      import module.*
      val target      = Offset(100)
      val adapted     = offsetProof.contramap[Offset, Int](Offset(7), target)(_ + 10)
      val before      = constructed.toVector
      val interpreter = adapted(target)
      assertTrue(
        before == Vector(7),
        interpreter eq adapted(target),
        interpreter(2) == Right(19),
        interpreter.applyMultiple(Vector(2, 3)) == Right(Vector(19, 20)),
        constructed.toVector == before
      )
    },
    test("syntax constructs child interpreters before reify and reuses them") {
      val module = new Binary
      import module.*
      val relation    = Offset(1) >>: (Offset(10) >>: Offset(100))
      val ops         = relationOps(relation)
      val beforeReify = constructed.toVector
      val interpreter = ops.reify
      assertTrue(
        beforeReify == Vector(1, 10, 100),
        interpreter eq ops.reify,
        interpreter(2) == Right(113),
        constructed.toVector == beforeReify
      )
    }
  )
}
