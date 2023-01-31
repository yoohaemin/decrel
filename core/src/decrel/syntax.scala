/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

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

    def :>:[LeftTree, LeftIn, LeftOut, ZippedOut](
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

    def :>:[LeftTree, LeftIn, LeftOut, ZippedOut](
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

    def :>:[LeftTree, LeftIn, LeftOutO, ZippedOut, CC[+A]](
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
    private val self: Tree & Relation[In, Out]
  ) {

    def customImpl: Relation.Custom[Tree & Relation[In, Out], In, Out] =
      Relation.Custom(self)
  }

}

object syntax extends syntax
