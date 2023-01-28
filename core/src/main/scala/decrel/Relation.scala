/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

sealed trait Relation[-In, +Out]

object Relation {

  trait Single[-In, +Out] extends Relation[In, Out]

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
  ) extends Relation[LeftIn, RightOut]
}

trait module {

  class Proof[Rel, -In, Out]

  object Proof {

    trait Single[Rel <: Relation.Single[In, Out], -In, Out] extends Proof[Rel, In, Out]

    def summon[Rel, In, Out](
      rel: Rel & Relation[In, Out]
    )(implicit
      ev: Proof[Rel, In, Out]
    ): Proof[Rel, In, Out] = ev

    implicit def composedSingleProof[
      LeftTree <: Relation.Single[LeftIn, LeftOut],
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Proof.Single[LeftTree, LeftIn, LeftOut],
      rightProof: Proof[RightTree, RightIn, RightOut],
      ev: LeftOut <:< RightIn
    ): Proof[
      Relation.ComposedSingle[
        LeftTree,
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut
      ],
      LeftIn,
      RightOut,
    ] = new Proof

  }

}

class DoesntCompile {

  trait Foo
  trait Bar
  trait Baz

  object a extends Relation.Single[Foo, Bar]
  object b extends Relation.Single[Bar, Baz]

  trait Proofs[F[*]] extends module {

    implicit def aProof: Proof.Single[a.type, Foo, Bar]
    implicit def bProof: Proof.Single[b.type, Bar, Baz]

  }

  def problem[X[*]](x: Proofs[X]) = {
    import x.*

    val ok = (a >>: b)
    Proof.summon(ok)

    Proof.summon(Relation.ComposedSingle(a, b))

    // Doesn't compile
     Proof.summon(a >>: b)
  }

}

implicit class RelationComposeSyntax[RightTree, RightIn, RightOut](
  private val right: RightTree & Relation[RightIn, RightOut]
) {

  def >>:[LeftTree, LeftIn, LeftOut](
    left: LeftTree & Relation.Single[LeftIn, LeftOut]
  )(implicit
    ev: LeftOut <:< RightIn
  ): Relation.ComposedSingle[
    LeftTree & Relation.Single[LeftIn, LeftOut],
    LeftIn,
    LeftOut,
    RightTree & Relation[RightIn, RightOut],
    RightIn,
    RightOut
  ] = Relation.ComposedSingle(left, right)
}
