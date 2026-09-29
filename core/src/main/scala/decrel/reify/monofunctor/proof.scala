/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.monofunctor

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
  abstract class Proof[Rel, In, Out, -Filter <: Predicate[?, ?]] {

    final def reify(relation: Rel & Relation[In, Out]): ReifiedRelation[In, Out] =
      reifyRelation(relation)

    private[decrel] def reifyRelation(
      relation: Rel & Relation[In, Out]
    ): ReifiedRelation[In, Out]

    private[decrel] def reifyFiltered(
      relationKey: Any,
      filter: Option[Filter]
    ): ReifiedRelation[In, Out] =
      if (filter.isEmpty)
        reifyRelation(relationKey.asInstanceOf[Rel & Relation[In, Out]])
      else
        throw new UnsupportedOperationException("This proof does not implement filtered access")
  }

  object Proof {

    sealed trait Declared[Rel, In, Out, -Filter <: Predicate[?, ?]]
        extends Proof[Rel, In, Out, Filter]

    sealed trait FilteredOptional[Rel <: Relation[In, Option[Out]], In, Out, -Filter <: Predicate[
      ?,
      ?
    ]] extends Proof[Rel, In, Option[Out], Filter]

    sealed trait FilteredMany[
      Rel <: Relation[In, CC[Out]],
      In,
      CC[+_],
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof[Rel, In, CC[Out], Filter]

    /**
     * Includes both Single and Self.
     */
    sealed trait GenericSingle[Rel <: Relation[In, Out], In, Out, -Filter <: Predicate[?, ?]]
        extends Proof[Rel, In, Out, Filter]

    final class SelfProof[Rel <: Relation.Self[A], A]
        extends Proof.GenericSingle[Rel, A, A, Nothing] {

      override private[decrel] def reifyRelation(
        relation: Rel & Relation[A, A]
      ): ReifiedRelation[A, A] =
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

    implicit def selfProof[Rel <: Relation.Self[A], A]: Proof.GenericSingle[Rel, A, A, Nothing] =
      _selfProof.asInstanceOf[Proof.GenericSingle[Rel, A, A, Nothing]]

    implicit def widenRelationProof[
      Rel <: Relation[In, Out],
      In,
      Out,
      Filter <: Predicate[?, ?]
    ](
      proof: Proof[Rel, In, Out, Filter]
    ): Proof[Rel & Relation[In, Out], In, Out, Filter] =
      proof.asInstanceOf[Proof[Rel & Relation[In, Out], In, Out, Filter]]

    abstract class Single[
      Rel <: Relation[In, Out],
      In,
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof.Declared[Rel, In, Out, Filter]
        with GenericSingle[Rel, In, Out, Filter] { outer =>

      private[decrel] def reifyFilteredOptional(
        relationKey: Any,
        filter: Option[Filter]
      ): ReifiedRelation[In, Option[Out]] =
        new ReifiedRelation.Custom[In, Option[Out]] {
          override def apply(in: In): Access[Option[Out]] =
            outer.reifyFiltered(relationKey, filter).apply(in).map(Some(_))

          override def applyMultiple[
            Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]
          ](
            in: Coll[In]
          ): Access[Coll[Option[Out]]] =
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
      ): Proof.Single[Rel2, In2, Out, Nothing] =
        new Proof.Single[Rel2 & Relation.Single[In2, Out], In2, Out, Nothing] {
          override private[decrel] def reifyRelation(
            relation: (Rel2 & Relation.Single[In2, Out]) & Relation[In2, Out]
          ): ReifiedRelation[In2, Out] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Out] =
            new ReifiedRelation.ComposedSingle(
              new ReifiedRelation.FromFunction(f),
              outer.reifyFiltered(relationKey, filter)
            )

          override private[decrel] def reifyFilteredOptional(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.ComposedSingle(
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
      ): Proof.Optional[Rel2, In2, Out, Nothing] =
        new Proof.Optional[Rel2, In2, Out, Nothing] {

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Option[Out]]
          ): ReifiedRelation[In2, Option[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.ComposedOptional(
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
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, CC, Out, Nothing] =
        new Proof.Many[Rel2, In2, CC, Out, Nothing] {

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, CC[Out]]
          ): ReifiedRelation[In2, CC[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, CC[Out]] =
            new ReifiedRelation.ComposedMany(
              new ReifiedRelation.FromFunction(f),
              outer.reifyFiltered(relationKey, filter)
            )
        }
    }

    abstract class Optional[
      Rel <: Relation.Optional[In, Out],
      In,
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof.Declared[Rel, In, Option[Out], Filter] { outer =>

      final def contramap[
        Rel2 <: Relation.Optional[In2, Out],
        In2
      ](
        rel: Rel2
      )(
        f: In2 => In
      ): Proof.Optional[Rel2, In2, Out, Nothing] =
        new Proof.Optional[Rel2, In2, Out, Nothing] {
          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Option[Out]]
          ): ReifiedRelation[In2, Option[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.ComposedSingle(
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
      ): Proof.Optional[Rel2, In2, Out, Nothing] =
        new Proof.Optional[Rel2, In2, Out, Nothing] {

          private type X[A] = Option[Option[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Option[Out]]
          ): ReifiedRelation[In2, Option[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Option[Out]] =
            new ReifiedRelation.Transformed[In2, X, Option, Out](
              new ReifiedRelation.ComposedOptional(
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
      )(implicit tagkColl: TagK[CC]): Proof.Many[Rel2, In2, CC, Out, Nothing] =
        new Proof.Many[Rel2, In2, CC, Out, Nothing] {

          private type X[A] = CC[Option[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, CC[Out]]
          ): ReifiedRelation[In2, CC[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, CC[Out]] =
            new ReifiedRelation.Transformed[In2, X, CC, Out](
              new ReifiedRelation.ComposedMany[In2, In, In, CC, Option[Out]](
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
      Coll[+T] <: Iterable[T] & IterableOps[T, Coll, Coll[T]],
      Out,
      -Filter <: Predicate[?, ?]
    ] extends Proof.Declared[Rel, In, Coll[Out], Filter] { outer =>

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
      ): Proof.Many[Rel2, In2, Coll2, Out, Nothing] =
        new Proof.Many[Rel2, In2, Coll2, Out, Nothing] {

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Coll2[Out]]
          ): ReifiedRelation[In2, Coll2[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, Coll, Coll2, Out](
              new ReifiedRelation.ComposedSingle[In2, In, In, Coll[Out]](
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
      ): Proof.Many[Rel2, In2, Coll2, Out, Nothing] =
        new Proof.Many[Rel2, In2, Coll2, Out, Nothing] {

          type X[A] = Option[Coll[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Coll2[Out]]
          ): ReifiedRelation[In2, Coll2[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, Coll2, Out](
              new ReifiedRelation.ComposedOptional[In2, In, In, Coll[Out]](
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
      ): Proof.Many[Rel2, In2, Coll2, Out, Nothing] =
        new Proof.Many[Rel2, In2, Coll2, Out, Nothing] {

          type X[A] = Coll2[Coll[A]]

          override private[decrel] def reifyRelation(
            relation: Rel2 & Relation[In2, Coll2[Out]]
          ): ReifiedRelation[In2, Coll2[Out]] =
            reifyFiltered(relation, None)

          override private[decrel] def reifyFiltered(
            relationKey: Any,
            filter: Option[Nothing]
          ): ReifiedRelation[In2, Coll2[Out]] =
            new ReifiedRelation.Transformed[In2, X, Coll2, Out](
              new ReifiedRelation.ComposedMany[In2, In, In, Coll2, Coll[Out]](
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
      Out,
      FilterValue <: Predicate[In, Out],
      ProofFilter >: FilterValue <: Predicate[?, ?]
    ](implicit
      proof: Proof.Single[Tree & Relation.Single[In, Out], In, Out, ProofFilter]
    ): Proof.FilteredOptional[
      Relation.Filtered[Tree, Out, In, Option[Out], FilterValue],
      In,
      Out,
      Nothing
    ] =
      new Proof.FilteredOptional[
        Relation.Filtered[Tree, Out, In, Option[Out], FilterValue],
        In,
        Out,
        Nothing
      ] {
        override private[decrel] def reifyRelation(
          relation: Relation.Filtered[Tree, Out, In, Option[Out], FilterValue] &
            Relation[In, Option[Out]]
        ): ReifiedRelation[In, Option[Out]] = {
          val filtered =
            relation.asInstanceOf[Relation.Filtered[Tree, Out, In, Option[Out], FilterValue]]
          proof.reifyFilteredOptional(filtered, Some(filtered.filter))
        }
      }

    implicit def filteredOptionalProof[
      Tree,
      In,
      Out,
      FilterValue <: Predicate[In, Out],
      ProofFilter >: FilterValue <: Predicate[?, ?]
    ](implicit
      proof: Proof.Optional[Tree & Relation.Optional[In, Out], In, Out, ProofFilter]
    ): Proof.FilteredOptional[
      Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue],
      In,
      Out,
      Nothing
    ] =
      new Proof.FilteredOptional[
        Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue],
        In,
        Out,
        Nothing
      ] {
        override private[decrel] def reifyRelation(
          relation: Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue] &
            Relation[In, Option[Out]]
        ): ReifiedRelation[In, Option[Out]] = {
          val filtered = relation.asInstanceOf[
            Relation.Filtered[Tree, Option[Out], In, Option[Out], FilterValue]
          ]
          proof.reifyFiltered(filtered, Some(filtered.filter))
        }
      }

    implicit def filteredManyProof[
      Tree,
      In,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
      Out,
      FilterValue <: Predicate[In, Out],
      ProofFilter >: FilterValue <: Predicate[?, ?]
    ](implicit
      proof: Proof.Many[Tree & Relation.Many[In, CC, Out], In, CC, Out, ProofFilter]
    ): Proof.FilteredMany[
      Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue],
      In,
      CC,
      Out,
      Nothing
    ] =
      new Proof.FilteredMany[
        Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue],
        In,
        CC,
        Out,
        Nothing
      ] {
        override private[decrel] def reifyRelation(
          relation: Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue] &
            Relation[In, CC[Out]]
        ): ReifiedRelation[In, CC[Out]] = {
          val filtered = relation.asInstanceOf[
            Relation.Filtered[Tree, CC[Out], In, CC[Out], FilterValue]
          ]
          proof.reifyFiltered(filtered, Some(filtered.filter))
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
      leftProof: Proof.GenericSingle[LeftTree, LeftIn, LeftOut, Nothing],
      rightProof: Proof[RightTree, RightIn, RightOut, Nothing],
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
      ): ReifiedRelation[LeftIn, RightOut] = {
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
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Proof.Optional[LeftTree, LeftIn, LeftOut, Nothing],
      rightProof: Proof[RightTree, RightIn, RightOut, Nothing],
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
      Option[RightOut],
      Nothing
    ] = new Proof[
      Relation.Composed.Optional[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut],
      LeftIn,
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
      ): ReifiedRelation[LeftIn, Option[RightOut]] = {
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
      LeftOut,
      LeftFilter <: Predicate[?, ?],
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Proof.FilteredOptional[
        Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, Option[LeftOut], LeftFilter],
        LeftIn,
        LeftOut,
        Nothing
      ],
      rightProof: Proof[RightTree, RightIn, RightOut, Nothing],
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
      ): ReifiedRelation[LeftIn, Option[RightOut]] = {
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
      LeftOut,
      RightTree,
      RightIn,
      RightOut,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](implicit
      leftProof: Proof.Many[LeftTree, LeftIn, CC, LeftOut, Nothing],
      rightProof: Proof[RightTree, RightIn, RightOut, Nothing],
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
      CC[RightOut],
      Nothing
    ] = new Proof[
      Relation.Composed.Many[LeftTree, LeftIn, LeftOut, RightTree, RightIn, RightOut, CC],
      LeftIn,
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
      ): ReifiedRelation[LeftIn, CC[RightOut]] = {
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
      LeftOut,
      LeftFilter <: Predicate[?, ?],
      RightTree,
      RightIn,
      RightOut,
      CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]
    ](implicit
      leftProof: Proof.FilteredMany[
        Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, CC[LeftOut], LeftFilter],
        LeftIn,
        CC,
        LeftOut,
        Nothing
      ],
      rightProof: Proof[RightTree, RightIn, RightOut, Nothing],
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
      ): ReifiedRelation[LeftIn, CC[RightOut]] = {
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
        LeftOutRefined,
        Nothing
      ],
      rightProof: Proof[
        RightTree & Relation[RightIn, RightOut],
        RightIn,
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
      ): ReifiedRelation[LeftIn, ZOR] = {
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

  implicit class relationOps[Rel, In, Out](val rel: Rel & Relation[In, Out]) {
    def reify(implicit ev: Proof[Rel, In, Out, Nothing]): ReifiedRelation[In, Out] =
      ev.reify(rel)
  }

}
