
sealed trait X
object X {
  case class Plus[L, R](left: L, right: R) extends X
}

sealed trait Y[X2, A]
object Y {
  given [L, R, A]: Y[X.Plus[L, R], A] = ???
}

object Test {
  def plus[L, R](left: L, right: R & X): X.Plus[L, R & X]  =
    X.Plus(left, right)

  def summon[X2, A](rel: X2)(implicit ev: Y[X2, A]): Y[X2, A] = ev

  object a
  object b extends X

  inline def ok = plus(a, b)
  summon(ok) // ok
  summon(X.Plus(a, b)) // ok
  summon {
    inline def ok = plus(a, b)
    ok
  } // ok
  summon(X.Plus(a, b): X.Plus[a.type, b.type & X]) // ok
  summon(plus(a, b): X.Plus[a.type, b.type & X]) // ok
  summon(plus(a, b)) // fails
}
