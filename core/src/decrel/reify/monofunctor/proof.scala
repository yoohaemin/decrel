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

trait proof { this: access & reifiedRelation =>

  /**
   * A `Proof` shows that a relation is reifiable as `In => Access[Out]`.
   *
   * In practice, this data structure is the outer shell of `ReifiedRelation`
   * that guides the implicit derivation mechanism.
   */
  // TODO: drop tparam Rel. It's only used for just a subset of cases.
  // Need to overhaul how
  abstract class Proof[+Rel, -In, Out] {

    def reify: ReifiedRelation[In, Out]
  }

  object Proof {

//    /**
//     * Proofs that are lifted from pure functions.
//     * The intention is for this to be used to implement contramap.
//     *
//     * subtypes are sealed because Scala 2 won't make it final -- for
//     * all intents and purposes sealed does the job.
//     */
//    sealed trait PureProof[Rel, -In, Out] extends Proof[Rel, In, Out] {
//      def rel: Rel
//    }
//    object PureProof {
//
//      sealed class Single[
//        Rel <: Relation.Single[In, Out],
//        -In,
//        Out
//      ](
//        relation: Rel,
//        f: In => Out
//      ) extends Proof.GenericSingle[Rel, In, Out]
//          with PureProof[Rel, In, Out] {
//
//        override final def rel: Rel = relation
//
//        override final val reify: ReifiedRelation[In, Out] =
//          new ReifiedRelation.Defined[In, Out] { //TODO defined?
//
//            override final def apply(in: In): Access[Out] = succeed(f(in))
//
//            override final def applyMultiple[
//              Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
//            ](
//              in: Coll[In]
//            ): Access[Coll[Out]] = succeed(in.map(f))
//          }
//
//      }
//
//      sealed class Optional[
//        Rel <: Relation.Optional[In, Out],
//        -In,
//        Out
//      ](
//        relation: Rel,
//        f: In => Option[Out]
//      ) extends Proof.Optional[Rel, In, Out]
//          with PureProof[Rel, In, Option[Out]] {
//
//        override final def rel: Rel = relation
//
//        override final val reify: ReifiedRelation[In, Option[Out]] =
//          new ReifiedRelation.Defined[In, Option[Out]] { //TODO defined?
//
//            override final def apply(in: In): Access[Option[Out]] = succeed(f(in))
//
//            override final def applyMultiple[
//              Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
//            ](
//              in: Coll[In]
//            ): Access[Coll[Option[Out]]] = succeed(in.map(f))
//          }
//      }
//
//      sealed class Many[
//        Rel <: Relation.Many[In, CC, Out],
//        -In,
//        CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
//        Out
//      ](
//        relation: Rel,
//        f: In => CC[Out]
//      ) extends Proof.Many[Rel, In, CC, Out]
//          with PureProof[Rel, In, CC[Out]] {
//
//        override def rel: Rel = relation
//
//        override final val reify: ReifiedRelation[In, CC[Out]] =
//          new ReifiedRelation.Defined[In, CC[Out]] { //TODO defined?
//
//          override final def apply(in: In): Access[CC[Out]] = succeed(f(in))
//
//          override final def applyMultiple[
//            Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
//          ](
//            in: Coll[In]
//          ): Access[Coll[CC[Out]]] = succeed(in.map(f))
//        }
//      }
//
//    }

    sealed trait Declared[+Rel, -In, Out] extends Proof[Rel, In, Out]

    /**
     * Includes both Single and Self.
     * Used for binding implicit search.
     */
    sealed trait GenericSingle[+Rel <: Relation[In, Out], -In, Out] extends Proof[Rel, In, Out]

    abstract class Single[+Rel <: Relation[In, Out], -In, Out]
        extends Proof.Declared[Rel, In, Out]
        with GenericSingle[Rel, In, Out] { outer =>

      def reify: ReifiedRelation[In, Out]

      final def contramap[
        Rel2 <: Relation.Single[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Single[Rel2, In2, Out] =
        new Proof.Single[Rel2 & Relation.Single[In2, Out], In2, Out] {
          override val reify: ReifiedRelation[In2, Out] =
            new ReifiedRelation.ComposedSingle(
              new ReifiedRelation.FromFunction(f),
              outer.reify
            )
        }

      final def contramapOptional[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => Option[In]
      ): Proof.Optional[Rel2, In2, Out] =
        new Proof.Optional[Rel2, In2, Out] {

          override val reify: ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.ComposedOptional(
              new ReifiedRelation.FromFunction(f),
              outer.reify
            )
        }

      final def contramapMany[
        Rel2 <: Relation.Many[In2, CC, Out],
        In2,
        CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
      ](
        rel: Rel2
      )(
        f: In2 => CC[In]
      ): Proof.Many[Rel2, In2, CC, Out] =
        new Proof.Many[Rel2, In2, CC, Out] {

          override val reify: ReifiedRelation[In2, CC[Out]] =
            new ReifiedRelation.ComposedMany(
              new ReifiedRelation.FromFunction(f),
              outer.reify
            )
        }
    }

    final class SelfProof[Rel <: Relation.Self[A], A] extends Proof.GenericSingle[Rel, A, A] {

      override val reify: ReifiedRelation[A, A] =
        new ReifiedRelation.Defined[A, A] { // TODO defined?
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
//            new ReifiedRelation.Defined[In2, Option[Out]] { //TODO defined?
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
      Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]],
      Out
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
      leftProof: Proof.GenericSingle[LeftTree, LeftIn, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightOut],
      ev: LeftOut <:< RightIn
    ): Proof.Single[
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
    ] = new Proof.Single[
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
    ] {
      override val reify: ReifiedRelation[LeftIn, RightOut] =
        new ReifiedRelation.ComposedSingle(leftProof.reify, rightProof.reify)
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
        new ReifiedRelation.ComposedOptional(leftProof.reify, rightProof.reify)
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
      leftProof: Proof.Many[LeftTree, LeftIn, CC, LeftOut],
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
        new ReifiedRelation.ComposedMany(leftProof.reify, rightProof.reify)
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
        new ReifiedRelation.Defined[LeftIn, ZOR] { self => // TODO defined?
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
