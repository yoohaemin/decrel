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

    sealed trait Declared[+Rel, -In, Out] extends Proof[Rel, In, Out]

    /**
     * Includes both Single and Self.
     * Used for binding implicit search.
     */
    sealed trait GenericSingle[+Rel <: Relation[In, Out], -In, Out] extends Proof[Rel, In, Out]

    final class SelfProof[Rel <: Relation.Self[A], A] extends Proof.GenericSingle[Rel, A, A] {

      override val reify: ReifiedRelation[A, A] =
        new ReifiedRelation.FromFunction(identity)
    }

    private val _selfProof: SelfProof[Relation.Self[Any], Any] =
      new SelfProof[Relation.Self[Any], Any]

    implicit def selfProof[Rel <: Relation.Self[A], A]: Proof.Single[Rel, A, A] =
      _selfProof.asInstanceOf[Proof.Single[Rel, A, A]]

    abstract class Single[+Rel <: Relation[In, Out], -In, Out]
      extends Proof.Declared[Rel, In, Out]
        with GenericSingle[Rel, In, Out]

    abstract class Optional[+Rel <: Relation.Optional[In, Out], -In, Out]
        extends Proof.Declared[Rel, In, Option[Out]]

    abstract class Many[
      Rel <: Relation.Many[In, Coll, Out],
      -In,
      Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]],
      Out
    ] extends Proof.Declared[Rel, In, Coll[Out]]

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
