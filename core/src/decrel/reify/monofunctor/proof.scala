/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.monofunctor

import decrel.*

import scala.collection.{ BuildFrom, IterableOps }

trait proof { this: access =>

  abstract class ReifiedRelation[-In, Out] {

    def apply(in: In): Access[Out]

    def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
      in: Coll[In]
    ): Access[Coll[Out]]

  }

  // Can I lift pure functions into proofs???? omgomgomg

  abstract class Proof[+Rel, -In, Out] {

    def reify: ReifiedRelation[In, Out]
  }

  object Proof {

    sealed trait Declared[+Rel, -In, Out] extends Proof[Rel, In, Out] {

//      type Self[R, I, O] <: Proof[R & Relation[I, O], I, O]
//
//      def contramap[Rel2, In2](
//        rel: Rel2
//      )(
//        f: In2 => In
//      ): Self[Rel2, In2, Out]

    }

    /**
     * Includes both Single and Self.
     * Used for binding implicit search.
     */
    sealed trait GenericSingle[+Rel <: Relation.Single[In, Out], -In, Out]
        extends Proof[Rel, In, Out]

    abstract class Single[+Rel <: Relation.Single[In, Out], -In, Out]
        extends Proof.Declared[Rel, In, Out]
        with GenericSingle[Rel, In, Out] { outer =>

      def reify: ReifiedRelation[In, Out]

      final def contramap[Rel2 <: Relation.Single[In2, Out], In2](
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Single[Rel2, In2, Out] =
        new Proof.Single[Rel2, In2, Out] {

          override def reify: ReifiedRelation[In2, Out] =
            new ReifiedRelation[In2, Out] {

              override def apply(in: In2): Access[Out] =
                outer.reify.apply(f(in))

              def applyMultiple[
                Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
              ](
                in: Coll[In2]
              ): Access[Coll[Out]] =
                outer.reify.applyMultiple(in.map(f))
            }
        }

      final def contramapOptional[Rel2 <: Relation.Optional[In2, Out], In2](
        rel: Rel2
      )(
        f: In2 => Option[In]
      ): Proof.Optional[Rel2, In2, Out] =
        new Proof.Optional[Rel2, In2, Out] {

          override def reify: ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation[In2, Option[Out]] {

              override def apply(in: In2): Access[Option[Out]] =
                f(in) match {
                  case Some(value) => outer.reify.apply(value).map(Some(_))
                  case None        => succeed(None)
                }

              def applyMultiple[
                Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
              ](
                ins: Coll[In2]
              ): Access[Coll[Option[Out]]] = {
                val optionals: Coll[Option[In]]    = ins.map(f)
                val flat: Iterable[In]             = optionals.flatten
                val results: Access[Iterable[Out]] = outer.reify.applyMultiple(flat)
                results.map { resultsIterable =>
                  val it = resultsIterable.iterator
                  optionals.map {
                    case Some(_) => Some(it.next())
                    case None    => None
                  }
                }
              }
            }
        }
    }

    final class SelfProof[Rel <: Relation.Self[A], A] extends Proof.GenericSingle[Rel, A, A] {

      override val reify: ReifiedRelation[A, A] =
        new ReifiedRelation[A, A] {
          override def apply(in: A): Access[A] = succeed(in)

          override def applyMultiple[Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]](
            in: Coll[A]
          ): Access[Coll[A]] = succeed(in)
        }
    }

    private val _selfProof: SelfProof[Relation.Self[Any], Any] =
      new SelfProof[Relation.Self[Any], Any]

    abstract class Optional[+Rel <: Relation.Optional[In, Out], -In, Out]
        extends Proof.Declared[Rel, In, Option[Out]] { outer =>

      def reify: ReifiedRelation[In, Option[Out]]

//      override final def contramap[Rel2, In2](
//        rel: Rel2
//      )(
//        f: In2 => In
//      ): Proof.Optional[Rel2 & Relation.Optional[In2, Out], In2, Out] =
//        new Proof.Optional[Rel2 & Relation.Optional[In2, Out], In2, Out] {
//
//          override def reify: ReifiedRelation[In2, Option[Out]] =
//            new ReifiedRelation[In2, Option[Out]] {
//
//              override def apply(in: In2): Access[Option[Out]] =
//                outer.reify.apply(f(in))
//
//              def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
//                in: Coll[In2]
//              ): Access[Coll[Option[Out]]] = outer.reify.applyMultiple(in.map(f))
//            }
//        }
    }

    abstract class Many[
      Rel <: Relation.Many[In, Coll, Out],
      -In,
      Out,
      Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
    ] extends Proof.Declared[Rel, In, Coll[Out]] {
      def reify: ReifiedRelation[In, Coll[Out]]
    }

    def summon[Rel, In, Out](
      rel: Rel & Relation[In, Out]
    )(implicit
      ev: Proof[Rel, In, Out]
    ): Proof[Rel, In, Out] = ev

    def reify[Rel, In, Out](
      rel: Rel & Relation[In, Out]
    )(implicit
      ev: Proof[Rel, In, Out]
    ): ReifiedRelation[In, Out] = ev.reify

    implicit def selfProof[Rel <: Relation.Self[A], A]: Proof.Single[Rel, A, A] =
      _selfProof.asInstanceOf[Proof.Single[Rel, A, A]]

    implicit def composedSingleProof[
      LeftTree <: Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Proof.Single[LeftTree, LeftIn, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightOut],
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
      RightOut
    ] = new Proof[
      Relation.Composed.Single[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      RightOut
    ] {
      override def reify: ReifiedRelation[LeftIn, RightOut] =
        new ReifiedRelation[LeftIn, RightOut] {

          override def apply(in: LeftIn): Access[RightOut] =
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
          ): Access[Coll[RightOut]] =
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
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Proof.Optional[LeftTree, LeftIn, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightOut],
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
      Option[RightOut]
    ] = new Proof[
      Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      Option[RightOut]
    ] {
      override def reify: ReifiedRelation[LeftIn, Option[RightOut]] =
        new ReifiedRelation[LeftIn, Option[RightOut]] {

          override def apply(in: LeftIn): Access[Option[RightOut]] =
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
          ): Access[Coll[Option[RightOut]]] =
            leftProof.reify
              .applyMultiple(in)
              .flatMap { (leftOuts: Coll[Option[LeftOut]]) =>
                type X[+A] = Coll[Option[A]]
                val inputs: Coll[Option[RightIn]] = ev.liftCo[X](leftOuts)
                val flat: Iterable[RightIn]       = inputs.flatten
                val results: Access[Iterable[RightOut]] =
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
      LeftOut,
      RightTree,
      RightIn,
      RightOut,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](implicit
      leftProof: Proof.Many[LeftTree, LeftIn, LeftOut, CC],
      rightProof: Proof[RightTree, RightIn, RightOut],
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
      CC[RightOut]
    ] = new Proof[
      Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
      LeftIn,
      CC[RightOut]
    ] {
      override def reify: ReifiedRelation[LeftIn, CC[RightOut]] =
        new ReifiedRelation[LeftIn, CC[RightOut]] {

          override def apply(in: LeftIn): Access[CC[RightOut]] =
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
          ): Access[Coll[CC[RightOut]]] =
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
      LeftOut,
      LeftOutRefined <: LeftOut,
      RightTree,
      RightIn <: LeftIn,
      RightOut,
      ZippedOut,
      ZOR <: ZippedOut
    ](implicit
      leftProof: Proof[
        LeftTree & Relation[LeftIn, LeftOut],
        LeftIn,
        LeftOutRefined
      ],
      rightProof: Proof[
        RightTree & Relation[RightIn, RightOut],
        RightIn,
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
      ZOR
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
      ZOR
    ] {
      override def reify: ReifiedRelation[LeftIn, ZOR] =
        new ReifiedRelation[LeftIn, ZOR] { self =>
          override def apply(in: LeftIn): Access[ZOR] =
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
          ): Access[Coll[ZOR]] =
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

  implicit class relationOps[Rel, In, Out](val rel: Rel & Relation[In, Out]) {
    def reify(implicit
      ev: Proof[Rel, In, Out]
    ): ReifiedRelation[In, Out] =
      ev.reify
  }

}
