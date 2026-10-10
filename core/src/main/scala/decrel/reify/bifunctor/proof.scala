/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.bifunctor

import decrel.*
import decrel.reify.CommonError
import izumi.reflect.TagK

import scala.collection.{ BuildFrom, IterableOps }
import scala.language.implicitConversions

trait proof { this: access & reifiedRelation =>

  /**
   * A `Proof` shows that a relation is reifiable as `In => Access[Out]`.
   *
   * Implicit construction binds relation values and builds the interpreter once.
   * `reify` exposes that interpreter; it never receives a runtime relation tree.
   */
  abstract class Proof[Rel, -In, +E, Out] {

    val reify: ReifiedRelation[In, E, Out]
  }

  trait ExistingProofs {
    implicit def existingProof[Rel, In, E, Out](implicit
      proof: Proof[Rel, In, E, Out]
    ): Rel => Proof[Rel, In, E, Out] = _ => proof
  }

  object Proof extends ExistingProofs {

    sealed trait Declared[Rel, -In, +E, Out] extends Proof[Rel, In, E, Out]

    /**
     * Includes both Single and Self.
     */
    sealed trait GenericSingle[Rel <: Relation[In, Out], -In, +E, Out]
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

    implicit def selfProof[Rel <: Relation.Self[A], A](
      rel: Rel
    ): Proof.GenericSingle[Rel, A, Nothing, A] =
      _selfProof.asInstanceOf[Proof.GenericSingle[Rel, A, Nothing, A]]

    abstract class Single[Rel <: Relation[In, Out], -In, +E, Out]
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

    abstract class Optional[Rel <: Relation.Optional[In, Out], -In, +E, Out]
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

    private def widen[In, E1, E2, Out](
      relation: ReifiedRelation[In, E1, Out],
      ev: E1 <:< E2
    ): ReifiedRelation[In, E2, Out] = {
      type Result[+E] = ReifiedRelation[In, E, Out]
      ev.substituteCo[Result](relation)
    }

    // Returning a conversion lets implicit search infer child errors before the relation is supplied.
    implicit def composedSingleProof[
      LeftTree <: Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftE,
      LeftOut,
      RightTree,
      RightIn,
      RightE,
      RightOut,
      E
    ](implicit
      leftProof: LeftTree => Proof[LeftTree, LeftIn, LeftE, LeftOut],
      rightProof: RightTree => Proof[RightTree, RightIn, RightE, RightOut],
      ev: LeftOut <:< RightIn,
      errors: CommonError[LeftE, RightE, E]
    ): (
      Relation.Composed.Single[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut
      ] => Proof.Single[
        Relation.Composed.Single[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
        LeftIn,
        E,
        RightOut
      ]
    ) = relation =>
      new Proof.Single[
        Relation.Composed.Single[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
        LeftIn,
        E,
        RightOut
      ] {
        override val reify: ReifiedRelation[LeftIn, E, RightOut] =
          new ReifiedRelation.ComposedSingle(
            widen(leftProof(relation.left).reify, errors.left),
            widen(rightProof(relation.right).reify, errors.right)
          )
      }

    implicit def composedOptionalProof[
      LeftTree <: Relation.Optional[LeftIn, LeftOut],
      LeftIn,
      LeftE,
      LeftOut,
      RightTree,
      RightIn,
      RightE,
      RightOut,
      E
    ](implicit
      leftProof: LeftTree => Proof[LeftTree, LeftIn, LeftE, Option[LeftOut]],
      rightProof: RightTree => Proof[RightTree, RightIn, RightE, RightOut],
      ev: LeftOut <:< RightIn,
      errors: CommonError[LeftE, RightE, E]
    ): (
      Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut] => Proof[
        Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
        LeftIn,
        E,
        Option[RightOut]
      ]
    ) = relation =>
      new Proof[
        Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
        LeftIn,
        E,
        Option[RightOut]
      ] {
        override val reify: ReifiedRelation[LeftIn, E, Option[RightOut]] =
          new ReifiedRelation.ComposedOptional(
            widen(leftProof(relation.left).reify, errors.left),
            widen(rightProof(relation.right).reify, errors.right)
          )
      }

    implicit def composedManyProof[
      LeftTree <: Relation.Many[LeftIn, CC, LeftOut],
      LeftIn,
      LeftE,
      LeftOut,
      RightTree,
      RightIn,
      RightE,
      RightOut,
      E,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](implicit
      leftProof: LeftTree => Proof[LeftTree, LeftIn, LeftE, CC[LeftOut]],
      rightProof: RightTree => Proof[RightTree, RightIn, RightE, RightOut],
      ev: LeftOut <:< RightIn,
      bf: BuildFrom[CC[RightIn], RightOut, CC[RightOut]],
      errors: CommonError[LeftE, RightE, E]
    ): (
      Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC] => Proof[
        Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
        LeftIn,
        E,
        CC[RightOut]
      ]
    ) = relation =>
      new Proof[
        Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
        LeftIn,
        E,
        CC[RightOut]
      ] {
        override val reify: ReifiedRelation[LeftIn, E, CC[RightOut]] =
          new ReifiedRelation.ComposedMany(
            widen(leftProof(relation.left).reify, errors.left),
            widen(rightProof(relation.right).reify, errors.right)
          )
      }

    implicit def composedZippedProof[
      LeftTree,
      LeftIn,
      LeftE,
      LeftOut,
      RightTree,
      RightIn,
      RightE,
      RightOut,
      ZippedOut,
      E
    ](implicit
      leftProof: LeftTree => Proof[LeftTree, LeftIn, LeftE, LeftOut],
      rightProof: RightTree => Proof[RightTree, RightIn, RightE, RightOut],
      zippable: Zippable.Out[LeftOut, RightOut, ZippedOut],
      zippedEv: LeftIn <:< RightIn,
      errors: CommonError[LeftE, RightE, E]
    ): (
      Relation.Composed.Zipped[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut,
        ZippedOut
      ] => Proof[
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
        E,
        ZippedOut
      ]
    ) = relation =>
      new Proof[Relation.Composed.Zipped[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut,
        ZippedOut
      ], LeftIn, E, ZippedOut] {
        override val reify: ReifiedRelation[LeftIn, E, ZippedOut] =
          new ReifiedRelation.Zipped(
            widen(leftProof(relation.left).reify, errors.left),
            widen(rightProof(relation.right).reify, errors.right)
          )
      }
  }

  final class RelationOps[Rel, In, E, Out](val proof: Proof[Rel, In, E, Out]) {
    def reify: ReifiedRelation[In, E, Out] = proof.reify
  }

  implicit def relationOps[Rel, In, E, Out](
    rel: Rel & Relation[In, Out]
  )(implicit toProof: Rel => Proof[Rel, In, E, Out]): RelationOps[Rel, In, E, Out] =
    new RelationOps(toProof(rel))

}
