/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.monofunctor

import decrel.*
import izumi.reflect.TagK

import scala.collection.{ BuildFrom, IterableOps }
import scala.language.implicitConversions

trait proof { this: access & reifiedRelation =>

  /**
   * A `Proof` shows that a relation is reifiable as `In => Access[Out]`.
   *
   * Applying a proof binds the actual relation value and constructs its interpreter.
   */
  abstract class Proof[Rel, -In, Out] {

    def apply(relation: Rel): ReifiedRelation[In, Out]
  }

  object Proof {

    sealed trait Declared[Rel, -In, Out] extends Proof[Rel, In, Out]

    /**
     * Includes both Single and Self.
     */
    sealed trait GenericSingle[Rel <: Relation[In, Out], -In, Out] extends Proof[Rel, In, Out]

    final class SelfProof[Rel <: Relation.Self[A], A] extends Proof.GenericSingle[Rel, A, A] {

      override def apply(relation: Rel): ReifiedRelation[A, A] = interpreter

      private val interpreter: ReifiedRelation[A, A] =
        // Same as new ReifiedRelation.FromFunction(identity)
        // but avoids traversing the collection.
        new ReifiedRelation.Custom[A, A] {

          override def apply(in: A): Access[A] = succeed(in)

          override def applyMultiple[
            Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]]
          ](
            in: Coll[A]
          ): Access[Coll[A]] = succeed(in)
        }
    }

    private val _selfProof: SelfProof[Relation.Self[Any], Any] =
      new SelfProof[Relation.Self[Any], Any]

    implicit def selfProof[Rel <: Relation.Self[A], A]: Proof.GenericSingle[Rel, A, A] =
      _selfProof.asInstanceOf[Proof.GenericSingle[Rel, A, A]]

    abstract class Single[Rel <: Relation[In, Out], -In, Out]
        extends Proof.Declared[Rel, In, Out]
        with GenericSingle[Rel, In, Out] { outer =>

      /**
       * To `contramap` a single relation with single function results in
       * a `Relation.Single`
       */
      final def contramap[
        Rel2 <: Relation.Single[In2, Out],
        In2
      ](
        source: Rel,
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Single[Rel2, In2, Out] =
        new Proof.Single[Rel2, In2, Out] {
          override def apply(relation: Rel2): ReifiedRelation[In2, Out] = interpreter

          private val interpreter: ReifiedRelation[In2, Out] =
            new ReifiedRelation.ComposedSingle(
              new ReifiedRelation.FromFunction(f),
              outer(source)
            )
        }

      final def contramapOptional[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        source: Rel,
        rel: Rel2
      )(
        f: In2 => Option[In]
      ): Proof.Optional[Rel2, In2, Out] =
        new Proof.Optional[Rel2, In2, Out] {

          override def apply(relation: Rel2): ReifiedRelation[In2, Option[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.ComposedOptional(
              new ReifiedRelation.FromFunction(f),
              outer(source)
            )
        }

      final def contramapMany[
        Rel2 <: Relation.Many[In2, CC, Out],
        In2,
        CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
      ](
        source: Rel,
        rel: Rel2
      )(
        f: In2 => CC[In]
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, CC, Out] =
        new Proof.Many[Rel2, In2, CC, Out] {

          override def apply(relation: Rel2): ReifiedRelation[In2, CC[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, CC[Out]] =
            new ReifiedRelation.ComposedMany(
              new ReifiedRelation.FromFunction(f),
              outer(source)
            )
        }
    }

    abstract class Optional[Rel <: Relation.Optional[In, Out], -In, Out]
        extends Proof.Declared[Rel, In, Option[Out]] { outer =>

      final def contramap[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        source: Rel,
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Optional[Rel2, In2, Out] =
        new Proof.Optional[Rel2, In2, Out] {
          override def apply(relation: Rel2): ReifiedRelation[In2, Option[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.ComposedSingle(
              new ReifiedRelation.FromFunction(f),
              outer(source)
            )
        }

      final def contramapOptional[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        source: Rel,
        rel: Rel2
      )(
        f: In2 => Option[In]
      ): Proof.Optional[Rel2, In2, Out] =
        new Proof.Optional[Rel2, In2, Out] {

          private type X[A] = Option[Option[A]]

          override def apply(relation: Rel2): ReifiedRelation[In2, Option[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.Transformed[In2, X, Option, Out](
              new ReifiedRelation.ComposedOptional(
                new ReifiedRelation.FromFunction(f),
                outer(source)
              ),
              _.flatten
            )
        }

      final def contramapMany[
        Rel2 <: Relation.Many[In2, CC, Out],
        In2,
        CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
      ](
        source: Rel,
        rel: Rel2
      )(
        f: In2 => CC[In]
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, CC, Out] =
        new Proof.Many[Rel2, In2, CC, Out] {

          private type X[A] = CC[Option[A]]

          override def apply(relation: Rel2): ReifiedRelation[In2, CC[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, CC[Out]] =
            new ReifiedRelation.Transformed[In2, X, CC, Out](
              new ReifiedRelation.ComposedMany[In2, In, In, CC, Option[Out]](
                new ReifiedRelation.FromFunction[In2, CC[In]](f),
                outer(source)
              ),
              _.flatten
            )
        }
    }

    abstract class Many[
      Rel <: Relation.Many[In, Coll, Out],
      -In,
      Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]],
      Out
    ] extends Proof.Declared[Rel, In, Coll[Out]] { outer =>

      final def contramap[
        Rel2 <: Relation.Many[In2, Coll2, Out],
        Coll2[+T] <: Iterable[T] & IterableOps[T, Coll2, Coll2[T]],
        In2
      ](
        source: Rel,
        rel: Rel2
      )(
        f: In2 => In
      )(implicit
        tagkColl: TagK[Coll],
        tagkColl2: TagK[Coll2],
        bf: BuildFrom[Coll[Out], Out, Coll2[Out]]
      ): Proof.Many[Rel2, In2, Coll2, Out] =
        new Proof.Many[Rel2, In2, Coll2, Out] {

          override def apply(relation: Rel2): ReifiedRelation[In2, Coll2[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, Coll, Coll2, Out](
              new ReifiedRelation.ComposedSingle[In2, In, In, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer(source)
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
        source: Rel,
        rel: Rel2
      )(
        f: In2 => Option[In]
      )(implicit
        tagkColl: TagK[Coll],
        tagkColl2: TagK[Coll2],
        bf: BuildFrom[Iterable[Out], Out, Coll2[Out]]
      ): Proof.Many[Rel2, In2, Coll2, Out] =
        new Proof.Many[Rel2, In2, Coll2, Out] {

          type X[A] = Option[Coll[A]]

          override def apply(relation: Rel2): ReifiedRelation[In2, Coll2[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, Coll2, Out](
              new ReifiedRelation.ComposedOptional[In2, In, In, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer(source)
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
        source: Rel,
        rel: Rel2
      )(
        f: In2 => Coll2[In]
      )(implicit
        tagkColl: TagK[Coll],
        tagkColl2: TagK[Coll2]
      ): Proof.Many[Rel2, In2, Coll2, Out] =
        new Proof.Many[Rel2, In2, Coll2, Out] {

          type X[A] = Coll2[Coll[A]]

          override def apply(relation: Rel2): ReifiedRelation[In2, Coll2[Out]] = interpreter

          private val interpreter: ReifiedRelation[In2, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, Coll2, Out](
              new ReifiedRelation.ComposedMany[In2, In, In, Coll2, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer(source)
              ),
              _.flatten
            )
        }
    }

    implicit def composedSingleProof[
      LeftTree <: Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Proof[LeftTree, LeftIn, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightOut],
      ev: LeftOut <:< RightIn
    ): Proof.Single[
      Relation.Composed.Single[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      RightOut
    ] =
      new Proof.Single[
        Relation.Composed.Single[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
        LeftIn,
        RightOut
      ] {
        override def apply(
          relation: Relation.Composed.Single[
            LeftTree,
            LeftIn,
            LeftOut,
            RightTree,
            RightIn,
            RightOut
          ]
        ): ReifiedRelation[LeftIn, RightOut] =
          new ReifiedRelation.ComposedSingle(
            leftProof(relation.left),
            rightProof(relation.right)
          )
      }

    implicit def composedOptionalProof[
      LeftTree <: Relation.Optional[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Proof[LeftTree, LeftIn, Option[LeftOut]],
      rightProof: Proof[RightTree, RightIn, RightOut],
      ev: LeftOut <:< RightIn
    ): Proof[
      Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      Option[RightOut]
    ] =
      new Proof[
        Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
        LeftIn,
        Option[RightOut]
      ] {
        override def apply(
          relation: Relation.Composed.Optional[
            LeftTree,
            LeftIn,
            LeftOut,
            RightTree,
            RightIn,
            RightOut
          ]
        ): ReifiedRelation[LeftIn, Option[RightOut]] =
          new ReifiedRelation.ComposedOptional(
            leftProof(relation.left),
            rightProof(relation.right)
          )
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
      leftProof: Proof[LeftTree, LeftIn, CC[LeftOut]],
      rightProof: Proof[RightTree, RightIn, RightOut],
      ev: LeftOut <:< RightIn,
      bf: BuildFrom[CC[RightIn], RightOut, CC[RightOut]]
    ): Proof[
      Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
      LeftIn,
      CC[RightOut]
    ] =
      new Proof[
        Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
        LeftIn,
        CC[RightOut]
      ] {
        override def apply(
          relation: Relation.Composed.Many[
            LeftTree,
            LeftIn,
            LeftOut,
            RightTree,
            RightIn,
            RightOut,
            CC
          ]
        ): ReifiedRelation[LeftIn, CC[RightOut]] =
          new ReifiedRelation.ComposedMany(
            leftProof(relation.left),
            rightProof(relation.right)
          )
      }

    implicit def composedZippedProof[
      LeftTree,
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut,
      ZippedOut
    ](implicit
      leftProof: Proof[LeftTree, LeftIn, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightOut],
      zippable: Zippable.Out[LeftOut, RightOut, ZippedOut],
      zippedEv: LeftIn <:< RightIn
    ): Proof[Relation.Composed.Zipped[
      LeftTree,
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut,
      ZippedOut
    ], LeftIn, ZippedOut] =
      new Proof[Relation.Composed.Zipped[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut,
        ZippedOut
      ], LeftIn, ZippedOut] {
        override def apply(
          relation: Relation.Composed.Zipped[
            LeftTree,
            LeftIn,
            LeftOut,
            RightTree,
            RightIn,
            RightOut,
            ZippedOut
          ]
        ): ReifiedRelation[LeftIn, ZippedOut] =
          new ReifiedRelation.Zipped(
            leftProof(relation.left),
            rightProof(relation.right)
          )
      }
  }

  final class RelationOps[In, Out](val reify: ReifiedRelation[In, Out])

  implicit def relationOps[Rel, In, Out](
    rel: Rel & Relation[In, Out]
  )(implicit proof: Proof[Rel, In, Out]): RelationOps[In, Out] =
    new RelationOps(proof(rel))

}
