/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

sealed trait X[In, Out]

object X {

  trait Y[In, Out] extends X[In, Out]

  case class ComposedSingle[
    LeftTree,
    LeftIn,
    LeftOut,
    RightTree,
    RightIn,
    RightOut
  ](
    left: LeftTree,
    right: RightTree
  ) extends X[LeftIn, RightOut]
}

trait module {

  class Z[Rel, -In, Out]

  object Z {

    trait Y[Rel <: X.Y[In, Out], In, Out] extends Z[Rel, In, Out]

    def summon[Rel, In, Out](
      rel: Rel & X[In, Out]
    )(implicit
      ev: Z[Rel, In, Out]
    ): Z[Rel, In, Out] = ev

    implicit def x[
      LeftTree <: X.Y[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Z.Y[LeftTree, LeftIn, LeftOut],
      rightProof: Z[RightTree, RightIn, RightOut],
      ev: LeftOut <:< RightIn
    ): Z[
      X.ComposedSingle[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut
      ],
      LeftIn,
      RightOut,
    ] = new Z

  }

}

class DoesntCompile {

  trait Foo
  trait Bar
  trait Baz

  object a extends X.Y[Foo, Bar]
  object b extends X.Y[Bar, Baz]

  trait Proofs[F[*]] extends module {

    implicit def aProof: Z.Y[a.type, Foo, Bar]
    implicit def bProof: Z.Y[b.type, Bar, Baz]

  }

  def problem[X[*]](x: Proofs[X]) = {
    import x.*

    val ok = (a >>: b)
    Z.summon(ok)

    Z.summon(X.ComposedSingle(a, b))

    // Doesn't compile
     Z.summon(a >>: b)
  }

}

implicit class RelationComposeSyntax[RightTree, RightIn, RightOut](
  private val right: RightTree & X[RightIn, RightOut]
) {

  def >>:[LeftTree, LeftIn, LeftOut](
    left: LeftTree & X.Y[LeftIn, LeftOut]
  )(implicit
    ev: LeftOut <:< RightIn
  ): X.ComposedSingle[
    LeftTree & X.Y[LeftIn, LeftOut],
    LeftIn,
    LeftOut,
    RightTree & X[RightIn, RightOut],
    RightIn,
    RightOut
  ] = X.ComposedSingle(left, right)
}
