/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.bifunctor

import decrel.*

import scala.collection.{ BuildFrom, IterableOps }

trait proof { this: access =>

  abstract class ReifiedRelation[-In, +E, +Out] {

    def apply(in: In): Access[E, Out]

    def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): Access[E, Coll[Out]]

  }

  abstract class Proof[Rel, -In, +E, +Out] {

    def reify: ReifiedRelation[In, E, Out]
  }

  object Proof {

    trait Single[Rel <: Relation.Single[In, Out], -In, +E, +Out] extends Proof[Rel, In, E, Out] {
      def reify: ReifiedRelation[In, E, Out]
    }

    trait Optional[Rel <: Relation.Optional[In, Out], -In, +E, +Out]
        extends Proof[Rel, In, E, Option[Out]] {
      def reify: ReifiedRelation[In, E, Option[Out]]
    }

    trait Many[
      Rel <: Relation.Many[In, Coll, Out],
      -In,
      +E,
      +Out,
      +Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
    ] extends Proof[Rel, In, E, Coll[Out]] {
      def reify: ReifiedRelation[In, E, Coll[Out]]
    }

    def summon[Rel, In, E, Out](
      rel: Rel & Relation[In, Out]
    )(implicit
      ev: Proof[Rel, In, E, Out]
    ): Proof[Rel, In, E, Out] = ev

    def reify[Rel, In, E, Out](
      rel: Rel & Relation[In, Out]
    )(implicit
      ev: Proof[Rel, In, E, Out]
    ): ReifiedRelation[In, E, Out] = ev.reify

    private final class SelfProof[Rel <: Relation.Self[A], A]
        extends Proof.Single[Rel, A, Nothing, A] {

      override val reify: ReifiedRelation[A, Nothing, A] =
        new ReifiedRelation[A, Nothing, A] {
          override def apply(in: A): Access[Nothing, A] = succeed(in)
          override def applyMultiple[Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]](
            in: Coll[A]
          ): Access[Nothing, Coll[A]] = succeed(in)
        }
    }

    private val _selfProof: SelfProof[Relation.Self[Any], Any] =
      new SelfProof[Relation.Self[Any], Any]

    implicit def selfProof[Rel <: Relation.Self[A], A]: Proof.Single[Rel, A, Nothing, A] =
      _selfProof.asInstanceOf[Proof.Single[Rel, A, Nothing, A]]

    implicit def composedSingleProof[
      LeftTree <: Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      RightTree,
      RightIn,
      RightE,
      RightOut
    ](implicit
      leftProof: Proof.Single[LeftTree, LeftIn, LeftE, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut],
      ev: LeftOut <:< RightIn
    ): Proof[
      Relation.Composed.Single[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut
      ],
      LeftIn,
      RightE,
      RightOut,
    ] = new Proof[
      Relation.Composed.Single[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      RightE,
      RightOut
    ] {
      override def reify: ReifiedRelation[LeftIn, RightE, RightOut] =
        new ReifiedRelation[LeftIn, RightE, RightOut] {

          override def apply(in: LeftIn): Access[RightE, RightOut] =
            leftProof.reify
              .apply(in)
              .flatMap { leftOut =>
                rightProof.reify
                  .apply(ev(leftOut))
              }

          override def applyMultiple[
            Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
          ](
            in: Coll[LeftIn]
          ): Access[RightE, Coll[RightOut]] =
            leftProof.reify
              .applyMultiple(in)
              .flatMap { leftOuts =>
                rightProof.reify
                  .applyMultiple(ev.liftCo(leftOuts))
              }
        }
    }

    implicit def composedOptionalProof[
      LeftTree <: Relation.Optional[LeftIn, LeftOut],
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      RightTree,
      RightIn,
      RightE,
      RightOut
    ](implicit
      leftProof: Proof.Optional[LeftTree, LeftIn, LeftE, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut],
      ev: LeftOut <:< RightIn
    ): Proof[
      Relation.Composed.Optional[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut
      ],
      LeftIn,
      RightE,
      Option[RightOut],
    ] = new Proof[
      Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      RightE,
      Option[RightOut]
    ] {
      override def reify: ReifiedRelation[LeftIn, RightE, Option[RightOut]] =
        new ReifiedRelation[LeftIn, RightE, Option[RightOut]] {

          override def apply(in: LeftIn): Access[RightE, Option[RightOut]] =
            leftProof.reify
              .apply(in)
              .flatMap { leftOut =>
                ev.liftCo(leftOut) match {
                  case Some(rightIn) =>
                    rightProof.reify
                      .apply(rightIn)
                      .map(Some(_))
                  case None =>
                    succeed(None)
                }
              }

          override def applyMultiple[
            Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
          ](
            in: Coll[LeftIn]
          ): Access[RightE, Coll[Option[RightOut]]] =
            leftProof.reify
              .applyMultiple(in)
              .flatMap { leftOuts =>
                type X[+A] = Coll[Option[A]]
                val inputs: Coll[Option[RightIn]] = ev.liftCo[X](leftOuts)
                val flat: Iterable[RightIn]       = inputs.flatten
                val results: Access[RightE, Iterable[RightOut]] =
                  rightProof.reify.applyMultiple(flat)
                results.map { resultsIterable =>
                  val it = resultsIterable.iterator
                  inputs.map {
                    case Some(_) => Some(it.next())
                    case None    => None
                  }
                }
              }
        }
    }

    implicit def composedManyProof[
      LeftTree <: Relation.Many[LeftIn, CC, LeftOut],
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      RightTree,
      RightIn,
      RightE,
      RightOut,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](implicit
      leftProof: Proof.Many[LeftTree, LeftIn, LeftE, LeftOut, CC],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut],
      ev: LeftOut <:< RightIn,
      bf: BuildFrom[CC[RightIn], RightOut, CC[RightOut]]
    ): Proof[
      Relation.Composed.Many[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut,
        CC
      ],
      LeftIn,
      RightE,
      CC[RightOut],
    ] = new Proof[
      Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
      LeftIn,
      RightE,
      CC[RightOut]
    ] {
      override def reify: ReifiedRelation[LeftIn, RightE, CC[RightOut]] =
        new ReifiedRelation[LeftIn, RightE, CC[RightOut]] {

          override def apply(in: LeftIn): Access[RightE, CC[RightOut]] =
            leftProof.reify
              .apply(in)
              .flatMap { leftOut =>
                val rightIns = ev.liftCo[CC](leftOut)
                rightProof.reify
                  .applyMultiple[CC](rightIns)
                  .map(_.to(bf.toFactory(rightIns)))
              }

          override def applyMultiple[Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]](
            in: Coll[LeftIn]
          ): Access[RightE, Coll[CC[RightOut]]] =
            leftProof.reify
              .applyMultiple(in)
              .flatMap { leftOuts =>
                type X[+A] = Coll[CC[A]]
                val inputs: Coll[CC[RightIn]] = ev.liftCo[X](leftOuts)
                val flattened                 = inputs.flatten

                val results = rightProof.reify.applyMultiple(flattened)
                results.map { resultsIterable =>
                  val it = resultsIterable.iterator
                  inputs.map(input => it.take(input.size).to(bf.toFactory(input)))
                }
              }
        }
    }

    implicit def composedZippedProof[
      LeftTree,
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      LeftOutRefined <: LeftOut,
      RightTree,
      RightIn <: LeftIn,
      RightE,
      RightOut,
      ZippedOut,
      ZOR <: ZippedOut
    ](implicit
      leftProof: Proof[
        LeftTree & Relation[LeftIn, LeftOut],
        LeftIn,
        LeftE,
        LeftOutRefined
      ],
      rightProof: Proof[
        RightTree & Relation[RightIn, RightOut],
        RightIn,
        RightE,
        RightOut
      ],
      zippable: Zippable.Out[LeftOutRefined, RightOut, ZOR],
      zippedEv: LeftIn <:< RightIn
    ): Proof[
      Relation.Composed.Zipped[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut,
        ZippedOut
      ],
      LeftIn,
      RightE,
      ZOR,
    ] = new Proof[
      Relation.Composed.Zipped[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut,
        ZippedOut
      ],
      LeftIn,
      RightE,
      ZOR,
    ] {
      override def reify: ReifiedRelation[LeftIn, RightE, ZOR] =
        new ReifiedRelation[LeftIn, RightE, ZOR] { self =>
          override def apply(in: LeftIn): Access[RightE, ZOR] =
            leftProof.reify
              .apply(in)
              .flatMap { leftOut =>
                rightProof.reify
                  .apply(zippedEv(in))
                  .map { rightOut =>
                    zippable.zip(leftOut, rightOut)
                  }
              }

          override def applyMultiple[Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]](
            in: Coll[LeftIn]
          ): Access[RightE, Coll[ZOR]] =
            leftProof.reify
              .applyMultiple(in)
              .flatMap { leftOut =>
                rightProof.reify
                  .applyMultiple(zippedEv.liftCo(in))
                  .map { rightOut =>
                    leftOut.zip(rightOut).map(p => zippable.zip(p._1, p._2))
                  }
              }
        }
    }
  }

  implicit class relationOps[Rel, In, E, Out](val rel: Rel & Relation[In, Out]) {
    def reify(implicit
      ev: Proof[Rel, In, E, Out]
    ): ReifiedRelation[In, E, Out] =
      ev.reify
  }

}
