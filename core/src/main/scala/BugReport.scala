
trait X[A, B]
class Y[A](a: A)

trait Test {
  implicit def xy[A, B]: X[Y[A], B]

  def summon[A, B](a: A)(implicit ev: X[A, B]): X[A, B] = ev

  // Doesn't have to be AnyRef
  def y[A](a: A & AnyRef): Y[A & AnyRef] = Y(a)
  object a

  val ok = y(a)
  summon(ok)   // ok
  summon(y(a)) // fails
}

/*
  summon(y(a)) // fails
              ^
No given instance of type X[Y[A & AnyRef], B] was found for parameter ev of method summon in object Test

where:    A is a type variable with constraint >: Test.a.type
          B is a type variable
.
I found:

    Test.xy[A, B]

But method xy in object Test does not match type X[Y[A & AnyRef], B].

 */
/*
16 |  summon(y(a)) // fails
   |              ^
   |No given instance of type X[Y[A & AnyRef], B] was found for parameter ev of method summon in trait Test
   |
   |where:    A is a type variable with constraint >: Test.this.a.type
   |          B is a type variable
   |.
   |I found:
   |
   |    this.xy[A, B]
   |
   |But method xy in trait Test does not match type X[Y[A & AnyRef], B].

 */