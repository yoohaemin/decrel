/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.bifunctor

import decrel.*
import izumi.reflect.TagK

import scala.collection.{ BuildFrom, IterableOps }

trait proof { this: access & reifiedRelation =>

  /**
   * A `Proof` shows that a relation is reifiable as `In => Access[Out]`.
   *
   * In practice, this data structure is the outer shell of `ReifiedRelation`
   * that guides the implicit derivation mechanism.
   */
  abstract class Proof[+Rel, -In, +E, Out] {

    def reify: ReifiedRelation[In, E, Out]
  }

  object Proof {

    sealed trait Declared[+Rel, -In, +E, Out] extends Proof[Rel, In, E, Out]

    /**
     * Includes both Single and Self.
     */
    sealed trait GenericSingle[+Rel <: Relation[In, Out], -In, +E, Out]
        extends Proof[Rel, In, E, Out]

    final class SelfProof[Rel <: Relation.Self[A], A]
        extends Proof.GenericSingle[Rel, A, Nothing, A] {

      override val reify: ReifiedRelation[A, Nothing, A] =
        // Same as new ReifiedRelation.FromFunction(identity)
        // but avoids traversing the collection.
        new ReifiedRelation.Custom[A, Nothing, A] {

          override def apply(in: A): Access[Nothing, A] = succeed(in)

          override def applyMultiple[
            Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
          ](
            in: Coll[A]
          ): Access[Nothing, Coll[A]] = succeed(in)
        }
    }

    private val _selfProof: SelfProof[Relation.Self[Any], Any] =
      new SelfProof[Relation.Self[Any], Any]

    implicit def selfProof[Rel <: Relation.Self[A], A]: Proof.GenericSingle[Rel, A, Nothing, A] =
      _selfProof.asInstanceOf[Proof.GenericSingle[Rel, A, Nothing, A]]

    abstract class Single[+Rel <: Relation[In, Out], -In, +E, Out]
        extends Proof.Declared[Rel, In, E, Out]
        with GenericSingle[Rel, In, E, Out] { outer =>

      /**
       * To `contramap` a single relation with single function results in
       * a `Relation.Single`
       */
      final def contramap[
        Rel2 <: Relation.Single[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Single[Rel2, In2, E, Out] =
        new Proof.Single[Rel2 & Relation.Single[In2, Out], In2, E, Out] {
          override val reify: ReifiedRelation[In2, E, Out] =
            new ReifiedRelation.ComposedSingle[In2, Nothing, In, In, E, Out](
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
      ): Proof.Optional[Rel2, In2, E, Out] =
        new Proof.Optional[Rel2, In2, E, Out] {

          override val reify: ReifiedRelation[In2, E, Option[Out]] =
            new ReifiedRelation.ComposedOptional[In2, Nothing, In, In, E, Out](
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
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, E, CC, Out] =
        new Proof.Many[Rel2, In2, E, CC, Out] {

          override val reify: ReifiedRelation[In2, E, CC[Out]] =
            new ReifiedRelation.ComposedMany(
              new ReifiedRelation.FromFunction(f),
              outer.reify
            )
        }
    }

    abstract class Optional[+Rel <: Relation.Optional[In, Out], -In, +E, Out]
        extends Proof.Declared[Rel, In, E, Option[Out]] { outer =>

      final def contramap[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Optional[Rel2, In2, E, Out] =
        new Proof.Optional[Rel2, In2, E, Out] {
          override val reify: ReifiedRelation[In2, E, Option[Out]] =
            new ReifiedRelation.ComposedSingle[In2, Nothing, In, In, E, Option[Out]](
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
      ): Proof.Optional[Rel2, In2, E, Out] =
        new Proof.Optional[Rel2, In2, E, Out] {

          private type X[A] = Option[Option[A]]

          override val reify: ReifiedRelation[In2, E, Option[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, Option, Out](
              new ReifiedRelation.ComposedOptional[In2, Nothing, In, In, E, Option[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reify
              ),
              _.flatten
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
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, E, CC, Out] =
        new Proof.Many[Rel2, In2, E, CC, Out] {

          private type X[A] = CC[Option[A]]

          override val reify: ReifiedRelation[In2, E, CC[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, CC, Out](
              new ReifiedRelation.ComposedMany[In2, Nothing, In, In, E, CC, Option[Out]](
                new ReifiedRelation.FromFunction[In2, CC[In]](f),
                outer.reify
              ),
              _.flatten
            )
        }
    }

    abstract class Many[
      Rel <: Relation.Many[In, Coll, Out],
      -In,
      +E,
      Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]],
      Out
    ] extends Proof.Declared[Rel, In, E, Coll[Out]] { outer =>

      final def contramap[
        Rel2 <: Relation.Many[In2, Coll2, Out],
        Coll2[+T] <: Iterable[T] & IterableOps[T, Coll2, Coll2[T]],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => In
      )(implicit
        tagkColl: TagK[Coll],
        tagkColl2: TagK[Coll2],
        bf: BuildFrom[Coll[Out], Out, Coll2[Out]]
      ): Proof.Many[Rel2, In2, E, Coll2, Out] =
        new Proof.Many[Rel2, In2, E, Coll2, Out] {

          override val reify: ReifiedRelation[In2, E, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, Coll, E, Coll2, Out](
              new ReifiedRelation.ComposedSingle[In2, Nothing, In, In, E, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reify
              ),
              (c: Coll[Out]) =>
                if (tagkColl.tag <:< tagkColl2.tag)
                  c.asInstanceOf[Coll2[Out]]
                else
                  bf.fromSpecific(c)(c)
            )
        }

      final def contramapOptional[
        Rel2 <: Relation.Many[In2, Coll2, Out],
        Coll2[+T] <: Iterable[T] & IterableOps[T, Coll2, Coll2[T]],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => Option[In]
      )(implicit
        tagkColl: TagK[Coll],
        tagkColl2: TagK[Coll2],
        bf: BuildFrom[Iterable[Out], Out, Coll2[Out]]
      ): Proof.Many[Rel2, In2, E, Coll2, Out] =
        new Proof.Many[Rel2, In2, E, Coll2, Out] {

          type X[A] = Option[Coll[A]]

          override val reify: ReifiedRelation[In2, E, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, Coll2, Out](
              new ReifiedRelation.ComposedOptional[In2, Nothing, In, In, E, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reify
              ),
              {
                case Some(c) =>
                  if (tagkColl.tag <:< tagkColl2.tag)
                    c.asInstanceOf[Coll2[Out]]
                  else
                    bf.fromSpecific(c)(c)
                case None =>
                  bf.fromSpecific(Iterable.empty)(Iterable.empty)
              }
            )
        }

      final def contramapMany[
        Rel2 <: Relation.Many[In2, Coll2, Out],
        Coll2[+A] <: Iterable[A] & IterableOps[A, Coll2, Coll2[A]],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => Coll2[In]
      )(implicit
        tagkColl: TagK[Coll],
        tagkColl2: TagK[Coll2]
      ): Proof.Many[Rel2, In2, E, Coll2, Out] =
        new Proof.Many[Rel2, In2, E, Coll2, Out] {

          type X[A] = Coll2[Coll[A]]

          override val reify: ReifiedRelation[In2, E, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, Coll2, Out](
              new ReifiedRelation.ComposedMany[In2, Nothing, In, In, E, Coll2, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reify
              ),
              _.flatten
            )
        }
    }

    implicit def composedSingleProof[
      LeftTree <: Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftE <: RightE,
      LeftOut <: RightIn,
      RightTree <: Relation[RightIn, RightOut],
      RightIn,
      RightE,
      RightOut
    ](implicit
      leftProof: Proof.GenericSingle[LeftTree, LeftIn, LeftE, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut]
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
      RightE,
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
      RightE,
      RightOut
    ] {
      override val reify: ReifiedRelation[LeftIn, RightE, RightOut] =
        new ReifiedRelation.ComposedSingle(leftProof.reify, rightProof.reify)
    }

    implicit def composedOptionalProof[
      LeftTree <: Relation.Optional[LeftIn, LeftOut],
      LeftIn,
      LeftE <: RightE,
      LeftOut <: RightIn,
      RightTree <: Relation[RightIn, RightOut],
      RightIn,
      RightE,
      RightOut
    ](implicit
      leftProof: Proof.Optional[LeftTree, LeftIn, LeftE, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut]
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
      Option[RightOut]
    ] = new Proof[
      Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      RightE,
      Option[RightOut]
    ] {
      override def reify: ReifiedRelation[LeftIn, RightE, Option[RightOut]] =
        new ReifiedRelation.ComposedOptional(leftProof.reify, rightProof.reify)
    }

    implicit def composedManyProof[
      LeftTree <: Relation.Many[LeftIn, CC, LeftOut],
      LeftIn,
      LeftE <: RightE,
      LeftOut <: RightIn,
      RightTree <: Relation[RightIn, RightOut],
      RightIn,
      RightE,
      RightOut,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](implicit
      leftProof: Proof.Many[LeftTree, LeftIn, LeftE, CC, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut],
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
      CC[RightOut]
    ] = new Proof[
      Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
      LeftIn,
      RightE,
      CC[RightOut]
    ] {
      override def reify: ReifiedRelation[LeftIn, RightE, CC[RightOut]] =
        new ReifiedRelation.ComposedMany(leftProof.reify, rightProof.reify)
    }

    implicit def composedZippedProof[
      LeftTree,
      LeftIn <: RightIn,
      LeftE <: RightE,
      LeftOut,
      LeftOutRefined <: LeftOut,
      RightTree,
      RightIn,
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
      zippable: Zippable.Out[LeftOutRefined, RightOut, ZOR]
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
      RightE,
      ZOR
    ] {
      override def reify: ReifiedRelation[LeftIn, RightE, ZOR] =
        new ReifiedRelation.Zipped(leftProof.reify, rightProof.reify)
    }
  }

  implicit class relationOps[Rel, In, E, Out](val rel: Rel & Relation[In, Out]) {
    def reify(implicit ev: Proof[Rel, In, E, Out]): ReifiedRelation[In, E, Out] =
      ev.reify
  }

}
