package decrel

sealed trait X
object X {
  case class Plus[L, R](left: L, right: R) extends X

  class Z[Rel, In]
  object Z {
    implicit def plus[L <: X, LeftIn, R]: Z[X.Plus[L, R], LeftIn] = ???
  }

  object a extends X
  object b extends X

  implicit def aa: Z[a.type, Int] = ???
  implicit def bb: Z[b.type, Int] = ???

  def summon[Rel, In](rel: Rel)(implicit ev: Z[Rel, In]): Z[Rel, In] = ev

  inline def ok = a + b
  summon(ok) // ok
  summon(X.Plus(a, b)) // ok
  summon {
    inline def ok = a + b
    ok
  } // ok
  summon(a + b) // fails
}

implicit class syntax[L](val left: L) {
  def +[R](right: R & X): X.Plus[L, R & X] =
    X.Plus(left, right)
}

// This compiles
//implicit class syntax[L](val left: L) {
//  def +[R](right: R): X.Plus[L, R] =
//    X.Plus(left, right)
//}
