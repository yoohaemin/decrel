package decrel

sealed trait X[In]

object X {

  trait Single[In] extends X[In]

  case class Composed[
    LeftTree,
    LeftIn,
    LeftOut,
    RightTree,
    RightIn,
    RightOut
  ](
    left: LeftTree,
    right: RightTree
  ) extends X[LeftIn]
}

trait module {

  class Z[Rel, -In]

  object Z {

    trait Single[Rel <: X.Single[In], In] extends Z[Rel, In]

    def summon[Rel, In](
      rel: Rel & X[In]
    )(implicit
      ev: Z[Rel, In]
    ): Z[Rel, In] = ev

    implicit def x[
      LeftTree <: X.Single[LeftIn],
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
        LeftIn,
        LeftOut,
        RightTree,
        RightIn,
        RightOut
      ],
      LeftIn,
    ] = new Z

  }

}

class DoesntCompile {

  trait Foo
  trait Bar
  trait Baz

  object a extends X.Single[Foo]
  object b extends X.Single[Bar]

  trait Proofs[F[*]] extends module {

    implicit def aProof: Z.Single[a.type, Foo]
    implicit def bProof: Z.Single[b.type, Bar]

  }

  def problem[X[*]](x: Proofs[X]) = {
    import x.*

    val ok = (a >>: b)
    Z.summon(ok)

    Z.summon(X.Composed(a, b))

    // Doesn't compile
     Z.summon(a >>: b)
  }

}

implicit class RelationComposeSyntax[RightTree, RightIn, RightOut](
  private val right: RightTree & X[RightIn]
) {

  def >>:[LeftTree, LeftIn, LeftOut](
    left: LeftTree & X.Single[LeftIn]
  )(implicit
    ev: LeftOut <:< RightIn
  ): X.Composed[
    LeftTree & X.Single[LeftIn],
    LeftIn,
    LeftOut,
    RightTree & X[RightIn],
    RightIn,
    RightOut
  ] = X.Composed(left, right)
}
