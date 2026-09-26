/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

import decrel.filter.{ Expr, Predicate }

import decrel.Relation.Composed

trait syntax {

  implicit final class RelationComposeSyntax[RightTree, RightIn, RightOut](
    private val right: RightTree & Relation[RightIn, RightOut]
  ) {

    def >>:[LeftTree, LeftIn, LeftOut](
      left: LeftTree & Relation.Single[LeftIn, LeftOut]
    )(implicit
      ev: LeftOut <:< RightIn
    ): Relation.Composed.Single[
      LeftTree & Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree & Relation[RightIn, RightOut],
      RightIn,
      RightOut
    ] = Relation.Composed.Single(left, right)

    def <>:[LeftTree, LeftIn, LeftOut, ZippedOut](
      left: LeftTree & Relation.Single[LeftIn, LeftOut]
    )(implicit
      ev: LeftOut <:< RightIn,
      zippable: Zippable.Out[LeftOut, RightOut, ZippedOut]
    ): Relation.Composed.Zipped[
      LeftTree & Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      Relation.Composed.Single[
        LeftTree & Relation.Single[LeftIn, LeftOut],
        LeftIn,
        LeftOut,
        RightTree & Relation[RightIn, RightOut],
        RightIn,
        RightOut
      ],
      LeftIn,
      RightOut,
      ZippedOut
    ] =
      Relation.Composed.Zipped(left, Relation.Composed.Single(left, right))

    def >>:[LeftTree, LeftIn, LeftOut](
      left: LeftTree & Relation.Optional[LeftIn, LeftOut]
    )(implicit
      ev: LeftOut <:< RightIn
    ): Relation.Composed.Optional[
      LeftTree & Relation.Optional[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree & Relation[RightIn, RightOut],
      RightIn,
      RightOut
    ] = Relation.Composed.Optional(left, right)

    def <>:[LeftTree, LeftIn, LeftOut, ZippedOut](
      left: LeftTree & Relation.Optional[LeftIn, LeftOut]
    )(implicit
      ev: LeftOut <:< RightIn,
      zippable: Zippable.Out[LeftOut, RightOut, ZippedOut]
    ): Composed.Optional[
      LeftTree & Relation.Optional[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      Composed.Zipped[
        Relation.Self[LeftOut],
        LeftOut,
        LeftOut,
        RightTree & Relation[RightIn, RightOut],
        RightIn,
        RightOut,
        ZippedOut
      ],
      LeftOut,
      ZippedOut
    ] =
      Relation.Composed.Optional(left, Relation.Composed.Zipped(Relation.Self[LeftOut], right))

    def >>:[LeftTree, LeftIn, LeftOut, CC[+A]](
      left: LeftTree & Relation.Many[LeftIn, CC, LeftOut]
    )(implicit
      ev: LeftOut <:< RightIn
    ): Relation.Composed.Many[
      LeftTree & Relation.Many[LeftIn, CC, LeftOut],
      LeftIn,
      LeftOut,
      RightTree & Relation[RightIn, RightOut],
      RightIn,
      RightOut,
      CC
    ] = Relation.Composed.Many(left, right)

    def <>:[LeftTree, LeftIn, LeftOutO, ZippedOut, CC[+A]](
      left: LeftTree & Relation.Many[LeftIn, CC, LeftOutO]
    )(implicit
      ev: LeftOutO <:< RightIn,
      zippable: Zippable.Out[LeftOutO, RightOut, ZippedOut]
    ): Composed.Many[
      LeftTree & Relation.Many[LeftIn, CC, LeftOutO],
      LeftIn,
      LeftOutO,
      Composed.Zipped[
        Relation.Self[LeftOutO],
        LeftOutO,
        LeftOutO,
        RightTree & Relation[RightIn, RightOut],
        RightIn,
        RightOut,
        ZippedOut
      ],
      LeftOutO,
      ZippedOut,
      CC
    ] =
      Relation.Composed.Many(left, Relation.Composed.Zipped(Relation.Self[LeftOutO], right))

    def >>:[LeftTree, LeftBaseOut, LeftIn, LeftOut, LeftFilter <: Predicate[?, ?]](
      left: Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, Option[LeftOut], LeftFilter]
    )(implicit
      ev: LeftOut <:< RightIn
    ): Relation.Composed.FilteredOptional[
      LeftTree,
      LeftBaseOut,
      LeftIn,
      LeftOut,
      LeftFilter,
      RightTree & Relation[RightIn, RightOut],
      RightIn,
      RightOut
    ] = Relation.Composed.FilteredOptional(left, right)

    def <>:[LeftTree, LeftBaseOut, LeftIn, LeftOut, LeftFilter <: Predicate[?, ?], ZippedOut](
      left: Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, Option[LeftOut], LeftFilter]
    )(implicit
      ev: LeftOut <:< RightIn,
      zippable: Zippable.Out[LeftOut, RightOut, ZippedOut]
    ): Relation.Composed.FilteredOptional[
      LeftTree,
      LeftBaseOut,
      LeftIn,
      LeftOut,
      LeftFilter,
      Composed.Zipped[
        Relation.Self[LeftOut],
        LeftOut,
        LeftOut,
        RightTree & Relation[RightIn, RightOut],
        RightIn,
        RightOut,
        ZippedOut
      ],
      LeftOut,
      ZippedOut
    ] =
      Relation.Composed.FilteredOptional(
        left,
        Relation.Composed.Zipped(Relation.Self[LeftOut], right)
      )

    def >>:[LeftTree, LeftBaseOut, LeftIn, LeftOut, LeftFilter <: Predicate[?, ?], CC[+A]](
      left: Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, CC[LeftOut], LeftFilter]
    )(implicit
      ev: LeftOut <:< RightIn
    ): Relation.Composed.FilteredMany[
      LeftTree,
      LeftBaseOut,
      LeftIn,
      LeftOut,
      LeftFilter,
      RightTree & Relation[RightIn, RightOut],
      RightIn,
      RightOut,
      CC
    ] = Relation.Composed.FilteredMany(left, right)

    def <>:[LeftTree, LeftBaseOut, LeftIn, LeftOutO, LeftFilter <: Predicate[?, ?], ZippedOut, CC[
      +A
    ]](
      left: Relation.Filtered[LeftTree, LeftBaseOut, LeftIn, CC[LeftOutO], LeftFilter]
    )(implicit
      ev: LeftOutO <:< RightIn,
      zippable: Zippable.Out[LeftOutO, RightOut, ZippedOut]
    ): Relation.Composed.FilteredMany[
      LeftTree,
      LeftBaseOut,
      LeftIn,
      LeftOutO,
      LeftFilter,
      Composed.Zipped[
        Relation.Self[LeftOutO],
        LeftOutO,
        LeftOutO,
        RightTree & Relation[RightIn, RightOut],
        RightIn,
        RightOut,
        ZippedOut
      ],
      LeftOutO,
      ZippedOut,
      CC
    ] =
      Relation.Composed.FilteredMany(left, Relation.Composed.Zipped(Relation.Self[LeftOutO], right))

  }

  implicit final class ZipSyntax[LeftTree, LeftIn, LeftOut](
    private val self: LeftTree & Relation[LeftIn, LeftOut]
  ) {

    /**
     * TODO add description
     */
    def zip[
      RightTree,
      RightIn,
      RightOut,
      ZippedOut
    ](
      that: RightTree & Relation[RightIn, RightOut]
    )(implicit
      zippable: Zippable.Out[LeftOut, RightOut, ZippedOut],
      ev: LeftIn <:< RightIn
    ): Relation.Composed.Zipped[
      LeftTree & Relation[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree & Relation[RightIn, RightOut],
      RightIn,
      RightOut,
      ZippedOut
    ] =
      Relation.Composed.Zipped(self, that)

    /**
     * A symbolic alias for `zip`.
     */
    def &[
      RightTree,
      RightIn <: LeftIn,
      RightOut,
      ZippedOut
    ](
      that: RightTree & Relation[RightIn, RightOut]
    )(implicit
      zippable: Zippable.Out[LeftOut, RightOut, ZippedOut],
      ev: LeftIn <:< RightIn
    ): Relation.Composed.Zipped[
      LeftTree & Relation[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree & Relation[RightIn, RightOut],
      RightIn,
      RightOut,
      ZippedOut
    ] =
      zip[RightTree, RightIn, RightOut, ZippedOut](that)

  }

  implicit final class CustomSyntax[Tree, In, Out](
    private val self: Tree & Relation.Declared[In, Out]
  ) {

    def customImpl: Relation.Custom[Tree & Relation.Declared[In, Out], In, Out] =
      Relation.Custom(self)
  }

  implicit final class SingleFilterSyntax[Tree, In, Out](
    private val self: Tree & Relation.Single[In, Out]
  ) {
    def filter(
      predicate: Predicate[In, Out]
    ): Relation.Filtered[Tree, Out, In, Option[Out], Predicate[In, Out]] =
      Relation.Filtered(self, predicate.checked)

    def filter(
      build: (Expr[In, Out, In], Expr[In, Out, Out]) => Predicate[In, Out]
    ): Relation.Filtered[Tree, Out, In, Option[Out], Predicate[In, Out]] =
      filter(Predicate.build(build))
  }

  implicit final class OptionalFilterSyntax[Tree, In, Out](
    private val self: Tree & Relation.Optional[In, Out]
  ) {
    def filter(
      predicate: Predicate[In, Out]
    ): Relation.Filtered[Tree, Option[Out], In, Option[Out], Predicate[In, Out]] =
      Relation.Filtered(self, predicate.checked)

    def filter(
      build: (Expr[In, Out, In], Expr[In, Out, Out]) => Predicate[In, Out]
    ): Relation.Filtered[Tree, Option[Out], In, Option[Out], Predicate[In, Out]] =
      filter(Predicate.build(build))
  }

  implicit final class ManyFilterSyntax[Tree, In, CC[+A], Out](
    private val self: Tree & Relation.Many[In, CC, Out]
  ) {
    def filter(
      predicate: Predicate[In, Out]
    ): Relation.Filtered[Tree, CC[Out], In, CC[Out], Predicate[In, Out]] =
      Relation.Filtered(self, predicate.checked)

    def filter(
      build: (Expr[In, Out, In], Expr[In, Out, Out]) => Predicate[In, Out]
    ): Relation.Filtered[Tree, CC[Out], In, CC[Out], Predicate[In, Out]] =
      filter(Predicate.build(build))
  }
}

object syntax extends syntax
