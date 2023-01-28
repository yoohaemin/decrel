package decrel

sealed trait X

object X {

  trait Single extends X

  case class Composed[
    LeftTree,
    RightTree,
  ](
    left: LeftTree,
    right: RightTree
  ) extends X
}

trait module {

  class Z[Rel, In]

  object Z {

    trait Single[Rel <: X.Single, In] extends Z[Rel, In]

    def summon[Rel, In](
      rel: Rel & X
    )(implicit
      ev: Z[Rel, In]
    ): Z[Rel, In] = ev

    implicit def x[
      LeftTree <: X.Single,
      LeftIn,
      LeftOut,
      RightTree,
      RightIn,
      RightOut
    ](implicit
      leftProof: Z.Single[LeftTree, LeftIn],
      rightProof: Z[RightTree, RightIn],
      ev: LeftOut <:< RightIn
    ): Z[
      X.Composed[
        LeftTree,
        RightTree,
      ],
      LeftIn,
    ] = new Z

  }

}

class DoesntCompile {

  trait Foo
  trait Bar

  object a extends X.Single
  object b extends X.Single

  trait Proofs extends module {

    implicit def aProof: Z.Single[a.type, Foo]
    implicit def bProof: Z.Single[b.type, Bar]

  }

  def problem(x: Proofs) = {
    import x.*

    val ok = (a >>: b)
    Z.summon(ok)

    Z.summon(X.Composed(a, b))

    // Doesn't compile
     Z.summon(a >>: b)
  }

}

implicit class RelationComposeSyntax[RightTree, RightIn, RightOut](
  private val right: RightTree & X
) {

  def >>:[LeftTree, LeftOut](
    left: LeftTree & X.Single
  )(implicit
    ev: LeftOut <:< RightIn
  ): X.Composed[
    LeftTree & X.Single,
    RightTree,
  ] = X.Composed(left, right)
}
