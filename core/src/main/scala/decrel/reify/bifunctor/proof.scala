/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.bifunctor

import decrel.*
import decrel.filter.Predicate
import izumi.reflect.TagK

import scala.collection.{ BuildFrom, IterableOps }
import scala.language.implicitConversions

trait proof { this: access & reifiedRelation =>

  /**
   * A `Proof` shows that a relation is reifiable as `In => Access[Out]`.
   *
   * In practice, this data structure is the outer shell of `ReifiedRelation`
   * that guides the implicit derivation mechanism.
   */
  abstract class Proof[Rel, In, +E, Out, -Filter <: Predicate[?, ?]] {

    final def reify(relation: Rel & Relation[In, Out]): ReifiedRelation[In, E, Out] =
      reifyRelation(relation)

    private[decrel] def reifyRelation(
      relation: Rel & Relation[In, Out]
    ): ReifiedRelation[In, E, Out]

    private[decrel] def reifyFiltered(
      relationKey: Any,
      filter: Option[Filter]
    ): ReifiedRelation[In, E, Out] =
      if (filter.isEmpty)
        reifyRelation(relationKey.asInstanceOf[Rel & Relation[In, Out]])
      else
        throw new UnsupportedOperationException("This proof does not implement filtered access")
  }

  object Proof {

    sealed trait Declared[Rel, In, +E, Out, -Filter <: Predicate[?, ?]]
        extends Proof[Rel, In, E, Out, Filter]

    sealed trait FilteredOptional[Rel <: Relation[
      In,
      Option[Out]
    ], In, +E, Out, -Filter <: Predicate[?, ?]]
        extends Proof[Rel, In, E, Option[Out], Filter]

    sealed trait FilteredMany[
      Rel <: Relation[In, CC[Out]],
      In,
      +E,
      CC[+_],
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof[Rel, In, E, CC[Out], Filter]

    /**
     * Includes both Single and Self.
     */
    sealed trait GenericSingle[Rel <: Relation[In, Out], In, +E, Out, -Filter <: Predicate[?, ?]]
        extends Proof[Rel, In, E, Out, Filter]

    final class SelfProof[Rel <: Relation.Self[A], A]
        extends Proof.GenericSingle[Rel, A, Nothing, A, Nothing] {

      override private[decrel] def reifyRelation(
        relation: Rel & Relation[A, A]
      ): ReifiedRelation[A, Nothing, A] =
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

    implicit def selfProof[Rel <: Relation.Self[A], A]
      : Proof.GenericSingle[Rel, A, Nothing, A, Nothing] =
      _selfProof.asInstanceOf[Proof.GenericSingle[Rel, A, Nothing, A, Nothing]]

    implicit def widenRelationProof[
      Rel <: Relation[In, Out],
      In,
      E,
      Out,
      Filter <: Predicate[?, ?]
    ](
      proof: Proof[Rel, In, E, Out, Filter]
    ): Proof[Rel & Relation[In, Out], In, E, Out, Filter] =
      proof.asInstanceOf[Proof[Rel & Relation[In, Out], In, E, Out, Filter]]

    abstract class Single[
      Rel <: Relation[In, Out],
      In,
      +E,
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof.Declared[Rel, In, E, Out, Filter]
        with GenericSingle[Rel, In, E, Out, Filter] { outer =>

      private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[In, E, Option[Out]] =
        new ReifiedRelation.Custom[In, E, Option[Out]] {
          override def apply(in: In): Access[E, Option[Out]] =
            outer.reifyFiltered(relationKey, filter).apply(in).map(Some(_))

          override def applyMultiple[
            Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
          ](
            in: Coll[In]
          ): Access[E, Coll[Option[Out]]] =
            outer.reifyFiltered(relationKey, filter).applyMultiple(in).map(_.map(Some(_)))
        }

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
      ): Proof.Single[Rel2, In2, E, Out, Nothing] =
        new Proof.Single[Rel2 & Relation.Single[In2, Out], In2, E, Out, Nothing] {
          override private[decrel] def reifyRelation(
            relation: (Rel2 & Relation.Single[In2, Out]) & Relation[In2, Out]
          ): ReifiedRelation[In2, E, Out] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Out] =
            new ReifiedRelation.ComposedSingle[In2, Nothing, In, In, E, Out](
              new ReifiedRelation.FromFunction(f),
              outer.reifyFiltered(relationKey, filter)
            )

          override private[decrel] def reifyFilteredOptional(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Option[Out]] =
            new ReifiedRelation.ComposedSingle[In2, Nothing, In, In, E, Option[Out]](
              new ReifiedRelation.FromFunction(f),
              outer.reifyFilteredOptional(relationKey, filter)
            )
        }

      final def contramapOptional[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => Option[In]
      ): Proof.Optional[Rel2, In2, E, Out, Nothing] =
        new Proof.Optional[Rel2, In2, E, Out, Nothing] {

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Option[Out]]
          ): ReifiedRelation[In2, E, Option[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Option[Out]] =
            new ReifiedRelation.ComposedOptional[In2, Nothing, In, In, E, Out](
              new ReifiedRelation.FromFunction(f),
              outer.reifyFiltered(relationKey, filter)
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
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, E, CC, Out, Nothing] =
        new Proof.Many[Rel2, In2, E, CC, Out, Nothing] {

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, CC[Out]]
          ): ReifiedRelation[In2, E, CC[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, CC[Out]] =
            new ReifiedRelation.ComposedMany(
              new ReifiedRelation.FromFunction(f),
              outer.reifyFiltered(relationKey, filter)
            )
        }
    }

    abstract class Optional[
      Rel <: Relation.Optional[In, Out],
      In,
      +E,
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof.Declared[Rel, In, E, Option[Out], Filter] { outer =>

      final def contramap[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Optional[Rel2, In2, E, Out, Nothing] =
        new Proof.Optional[Rel2, In2, E, Out, Nothing] {
          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Option[Out]]
          ): ReifiedRelation[In2, E, Option[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Option[Out]] =
            new ReifiedRelation.ComposedSingle[In2, Nothing, In, In, E, Option[Out]](
              new ReifiedRelation.FromFunction(f),
              outer.reifyFiltered(relationKey, filter)
            )
        }

      final def contramapOptional[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => Option[In]
      ): Proof.Optional[Rel2, In2, E, Out, Nothing] =
        new Proof.Optional[Rel2, In2, E, Out, Nothing] {

          private type X[A] = Option[Option[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Option[Out]]
          ): ReifiedRelation[In2, E, Option[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Option[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, Option, Out](
              new ReifiedRelation.ComposedOptional[In2, Nothing, In, In, E, Option[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reifyFiltered(relationKey, filter)
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
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, E, CC, Out, Nothing] =
        new Proof.Many[Rel2, In2, E, CC, Out, Nothing] {

          private type X[A] = CC[Option[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, CC[Out]]
          ): ReifiedRelation[In2, E, CC[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, CC[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, CC, Out](
              new ReifiedRelation.ComposedMany[In2, Nothing, In, In, E, CC, Option[Out]](
                new ReifiedRelation.FromFunction[In2, CC[In]](f),
                outer.reifyFiltered(relationKey, filter)
              ),
              _.flatten
            )
        }
    }

    abstract class Many[
      Rel <: Relation.Many[In, Coll, Out],
      In,
      +E,
      Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]],
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof.Declared[Rel, In, E, Coll[Out], Filter] { outer =>

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
      ): Proof.Many[Rel2, In2, E, Coll2, Out, Nothing] =
        new Proof.Many[Rel2, In2, E, Coll2, Out, Nothing] {

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Coll2[Out]]
          ): ReifiedRelation[In2, E, Coll2[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, Coll, E, Coll2, Out](
              new ReifiedRelation.ComposedSingle[In2, Nothing, In, In, E, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reifyFiltered(relationKey, filter)
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
      ): Proof.Many[Rel2, In2, E, Coll2, Out, Nothing] =
        new Proof.Many[Rel2, In2, E, Coll2, Out, Nothing] {

          type X[A] = Option[Coll[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Coll2[Out]]
          ): ReifiedRelation[In2, E, Coll2[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, Coll2, Out](
              new ReifiedRelation.ComposedOptional[In2, Nothing, In, In, E, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reifyFiltered(relationKey, filter)
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
      ): Proof.Many[Rel2, In2, E, Coll2, Out, Nothing] =
        new Proof.Many[Rel2, In2, E, Coll2, Out, Nothing] {

          type X[A] = Coll2[Coll[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Coll2[Out]]
          ): ReifiedRelation[In2, E, Coll2[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, E, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, E, Coll2, Out](
              new ReifiedRelation.ComposedMany[In2, Nothing, In, In, E, Coll2, Coll[Out]](
                new ReifiedRelation.FromFunction(f),
                outer.reifyFiltered(relationKey, filter)
              ),
              _.flatten
            )
        }
    }

    implicit def filteredSingleProof[
      Tree,
      In,
      E,
      Out,
      FilterValue <: Predicate[In, Out],
      ProofFilter >: FilterValue <: Predicate[?, ?]
    ](implicit
      proof: Proof.Single[Tree & Relation.Single[In, Out], In, E, Out, ProofFilter]
    ): Proof.FilteredOptional[
      Relation.Filtered[Tree, Out, In, Option[Out], FilterValue],
      In,
      E,
      Out,
      Nothing
    ] =
      new Proof.FilteredOptional[
        Relation.Filtered[Tree, Out, In, Option[Out], FilterValue],
        In,
        E,
        Out,
        Nothing
      ] {
        override private[decrel] def reifyRelation(
          relation: Relation.Filtered[Tree, Out, In, Option[Out], FilterValue] &
            Relation[In, Option[Out]]
        ): ReifiedRelation[In, E, Option[Out]] = {
          val filtered =
            relation.asInstanceOf[Relation.Filtered[Tree, Out, In, Option[Out], FilterValue]]
          proof.reifyFilteredOptional(filtered, Some(filtered.filter))
        }
      }

    implicit def filteredOptionalProof[
      Tree,
      In,
      E,
      Out,
      FilterValue <: Predicate[In, Out],
      ProofFilter >: FilterValue <: Predicate[?, ?]
    ](implicit
      proof: Proof.Optional[Tree & Relation.Optional[In, Out], In, E, Out, ProofFilter]
    ): Proof.FilteredOptional[
      Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue],
      In,
      E,
      Out,
      Nothing
    ] =
      new Proof.FilteredOptional[
        Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue],
        In,
        E,
        Out,
        Nothing
      ] {
        override private[decrel] def reifyRelation(
          relation: Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue] &
            Relation[In, Option[Out]]
        ): ReifiedRelation[In, E, Option[Out]] = {
          val filtered = relation.asInstanceOf[
            Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue]
          ]
          proof.reifyFiltered(filtered, Some(filtered.filter))
        }
      }

    implicit def filteredManyProof[
      Tree,
      In,
      E,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
      Out,
      FilterValue <: Predicate[In, Out],
      ProofFilter >: FilterValue <: Predicate[?, ?]
    ](implicit
      proof: Proof.Many[Tree & Relation.Many[In, CC, Out], In, E, CC, Out, ProofFilter]
    ): Proof.FilteredMany[
      Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue],
      In,
      E,
      CC,
      Out,
      Nothing
    ] =
      new Proof.FilteredMany[
        Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue],
        In,
        E,
        CC,
        Out,
        Nothing
      ] {
        override private[decrel] def reifyRelation(
          relation: Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue] &
            Relation[In, CC[Out]]
        ): ReifiedRelation[In, E, CC[Out]] = {
          val filtered = relation.asInstanceOf[
            Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue]
          ]
          proof.reifyFiltered(filtered, Some(filtered.filter))
        }
      }

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
      leftProof: Proof.GenericSingle[LeftTree, LeftIn, LeftE, LeftOut, Nothing],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut, Nothing],
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
      RightE,
      RightOut,
      Nothing
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
      RightOut,
      Nothing
    ] {
      override private[decrel] def reifyRelation(
        relation: Relation.Composed.Single[
          LeftTree,
          LeftIn,
          LeftOut,
          RightTree,
          RightIn,
          RightOut
        ] & Relation[LeftIn, RightOut]
      ): ReifiedRelation[LeftIn, RightE, RightOut] = {
        val composed = relation
          .asInstanceOf[
            Relation.Composed.Single[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut]
          ]
        new ReifiedRelation.ComposedSingle(
          leftProof.reify(composed.left.asInstanceOf[LeftTree & Relation.Single[LeftIn, LeftOut]]),
          rightProof.reify(composed.right.asInstanceOf[RightTree & Relation[RightIn, RightOut]])
        )
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
      leftProof: Proof.Optional[LeftTree, LeftIn, LeftE, LeftOut, Nothing],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut, Nothing],
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
      Nothing
    ] = new Proof[
      Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
      RightE,
      Option[RightOut],
      Nothing
    ] {
      override private[decrel] def reifyRelation(
        relation: Relation.Composed.Optional[
          LeftTree,
          LeftIn,
          LeftOut,
          RightTree,
          RightIn,
          RightOut
        ] & Relation[LeftIn, Option[RightOut]]
      ): ReifiedRelation[LeftIn, RightE, Option[RightOut]] = {
        val composed = relation.asInstanceOf[
          Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut]
        ]
        new ReifiedRelation.ComposedOptional(
          leftProof.reify(
            composed.left.asInstanceOf[LeftTree & Relation.Optional[LeftIn, LeftOut]]
          ),
          rightProof.reify(composed.right.asInstanceOf[RightTree & Relation[RightIn, RightOut]])
        )
      }
    }

    implicit def composedFilteredOptionalProof[
      LeftTree,
      LeftBaseOut,
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      LeftFilter <: Predicate[?, ?],
      RightTree,
      RightIn,
      RightE,
      RightOut
    ](implicit
      leftProof: Proof.FilteredOptional[
        Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, Option[LeftOut], LeftFilter],
        LeftIn,
        LeftE,
        LeftOut,
        Nothing
      ],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut, Nothing],
      ev: LeftOut <:< RightIn
    ): Proof[
      Relation.Composed.FilteredOptional[
        LeftTree,
        LeftBaseOut,
        LeftIn,
        LeftOut,
        LeftFilter,
        RightTree,
        RightIn,
        RightOut
      ],
      LeftIn,
      RightE,
      Option[RightOut],
      Nothing
    ] = new Proof[
      Relation.Composed.FilteredOptional[
        LeftTree,
        LeftBaseOut,
        LeftIn,
        LeftOut,
        LeftFilter,
        RightTree,
        RightIn,
        RightOut
      ],
      LeftIn,
      RightE,
      Option[RightOut],
      Nothing
    ] {
      override private[decrel] def reifyRelation(
        relation: Relation.Composed.FilteredOptional[
          LeftTree,
          LeftBaseOut,
          LeftIn,
          LeftOut,
          LeftFilter,
          RightTree,
          RightIn,
          RightOut
        ] & Relation[LeftIn, Option[RightOut]]
      ): ReifiedRelation[LeftIn, RightE, Option[RightOut]] = {
        val composed = relation.asInstanceOf[
          Relation.Composed.FilteredOptional[
            LeftTree,
            LeftBaseOut,
            LeftIn,
            LeftOut,
            LeftFilter,
            RightTree,
            RightIn,
            RightOut
          ]
        ]
        new ReifiedRelation.ComposedOptional(
          leftProof.reify(
            composed.left.asInstanceOf[
              Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, Option[LeftOut], LeftFilter] &
                Relation[LeftIn, Option[LeftOut]]
            ]
          ),
          rightProof.reify(composed.right.asInstanceOf[RightTree & Relation[RightIn, RightOut]])
        )
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
      leftProof: Proof.Many[LeftTree, LeftIn, LeftE, CC, LeftOut, Nothing],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut, Nothing],
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
      Nothing
    ] = new Proof[
      Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
      LeftIn,
      RightE,
      CC[RightOut],
      Nothing
    ] {
      override private[decrel] def reifyRelation(
        relation: Relation.Composed.Many[
          LeftTree,
          LeftIn,
          LeftOut,
          RightTree,
          RightIn,
          RightOut,
          CC
        ] & Relation[LeftIn, CC[RightOut]]
      ): ReifiedRelation[LeftIn, RightE, CC[RightOut]] = {
        val composed = relation.asInstanceOf[
          Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC]
        ]
        new ReifiedRelation.ComposedMany(
          leftProof.reify(
            composed.left.asInstanceOf[LeftTree & Relation.Many[LeftIn, CC, LeftOut]]
          ),
          rightProof.reify(composed.right.asInstanceOf[RightTree & Relation[RightIn, RightOut]])
        )
      }
    }

    implicit def composedFilteredManyProof[
      LeftTree,
      LeftBaseOut,
      LeftIn,
      LeftE <: RightE,
      LeftOut,
      LeftFilter <: Predicate[?, ?],
      RightTree,
      RightIn,
      RightE,
      RightOut,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](implicit
      leftProof: Proof.FilteredMany[
        Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, CC[LeftOut], LeftFilter],
        LeftIn,
        LeftE,
        CC,
        LeftOut,
        Nothing
      ],
      rightProof: Proof[RightTree, RightIn, RightE, RightOut, Nothing],
      ev: LeftOut <:< RightIn,
      bf: BuildFrom[CC[RightIn], RightOut, CC[RightOut]]
    ): Proof[
      Relation.Composed.FilteredMany[
        LeftTree,
        LeftBaseOut,
        LeftIn,
        LeftOut,
        LeftFilter,
        RightTree,
        RightIn,
        RightOut,
        CC
      ],
      LeftIn,
      RightE,
      CC[RightOut],
      Nothing
    ] = new Proof[
      Relation.Composed.FilteredMany[
        LeftTree,
        LeftBaseOut,
        LeftIn,
        LeftOut,
        LeftFilter,
        RightTree,
        RightIn,
        RightOut,
        CC
      ],
      LeftIn,
      RightE,
      CC[RightOut],
      Nothing
    ] {
      override private[decrel] def reifyRelation(
        relation: Relation.Composed.FilteredMany[
          LeftTree,
          LeftBaseOut,
          LeftIn,
          LeftOut,
          LeftFilter,
          RightTree,
          RightIn,
          RightOut,
          CC
        ] & Relation[LeftIn, CC[RightOut]]
      ): ReifiedRelation[LeftIn, RightE, CC[RightOut]] = {
        val composed = relation.asInstanceOf[
          Relation.Composed.FilteredMany[
            LeftTree,
            LeftBaseOut,
            LeftIn,
            LeftOut,
            LeftFilter,
            RightTree,
            RightIn,
            RightOut,
            CC
          ]
        ]
        new ReifiedRelation.ComposedMany(
          leftProof.reify(
            composed.left.asInstanceOf[
              Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, CC[LeftOut], LeftFilter] &
                Relation[LeftIn, CC[LeftOut]]
            ]
          ),
          rightProof.reify(composed.right.asInstanceOf[RightTree & Relation[RightIn, RightOut]])
        )
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
        LeftOutRefined,
        Nothing
      ],
      rightProof: Proof[
        RightTree & Relation[RightIn, RightOut],
        RightIn,
        RightE,
        RightOut,
        Nothing
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
      Nothing
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
      Nothing
    ] {
      override private[decrel] def reifyRelation(
        relation: Relation.Composed.Zipped[
          LeftTree,
          LeftIn,
          LeftOut,
          RightTree,
          RightIn,
          RightOut,
          ZippedOut
        ] & Relation[LeftIn, ZOR]
      ): ReifiedRelation[LeftIn, RightE, ZOR] = {
        val composed = relation.asInstanceOf[
          Relation.Composed.Zipped[
            LeftTree,
            LeftIn,
            LeftOut,
            RightTree,
            RightIn,
            RightOut,
            ZippedOut
          ]
        ]
        new ReifiedRelation.Zipped(
          leftProof.reify(
            composed.left.asInstanceOf[
              (LeftTree & Relation[LeftIn, LeftOut]) & Relation[LeftIn, LeftOutRefined]
            ]
          ),
          rightProof.reify(composed.right.asInstanceOf[RightTree & Relation[RightIn, RightOut]])
        )
      }
    }
  }

  implicit class relationOps[Rel, In, E, Out](val rel: Rel & Relation[In, Out]) {
    def reify(implicit ev: Proof[Rel, In, E, Out, Nothing]): ReifiedRelation[In, E, Out] =
      ev.reify(rel)
  }

}
