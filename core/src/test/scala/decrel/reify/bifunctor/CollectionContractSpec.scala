package decrel.reify.bifunctor

import zio.test.*

object CollectionContractSpec extends ZIOSpecDefault {
  object Interpreter extends access with proof with reifiedRelation {
    type Access[+E, +A] = A
    protected def succeed[A](a: A): Access[Nothing, A]                                  = a
    protected def flatMap[E, A, B](a: Access[E, A])(f: A => Access[E, B]): Access[E, B] = f(a)
    protected def map[E, A, B](a: Access[E, A])(f: A => B): Access[E, B]                = f(a)
    val single   = new ReifiedRelation.FromFunction[Int, Int](_ => 0)
    val optional = new ReifiedRelation.ComposedOptional[Int, Nothing, Int, Int, Nothing, Int](
      new ReifiedRelation.FromFunction[Int, Option[Int]](n => if (n == 0) None else Some(n)),
      single
    )
    val many = new ReifiedRelation.ComposedMany[Int, Nothing, Int, Int, Nothing, Vector, Int](
      new ReifiedRelation.FromFunction[Int, Vector[Int]](n => Vector.fill(n)(n)),
      single
    )
  }
  def spec: Spec[Environment, Any] = suite("ordered bulk contract")(
    test("optional traversal keeps empty positions and equal outputs") {
      assertTrue(
        Interpreter.optional
          .applyMultiple(Vector(1, 0, 2, 1)) == Vector(Some(0), None, Some(0), Some(0)),
        Interpreter.optional.applyMultiple(List.empty[Int]) == List.empty[Option[Int]]
      )
    },
    test("many traversal preserves duplicates and group boundaries") {
      assertTrue(
        Interpreter.many.applyMultiple(List(2, 0, 1, 2)) == List(
          Vector(0, 0),
          Vector.empty,
          Vector(0),
          Vector(0, 0)
        ),
        Interpreter.many.applyMultiple(Vector.empty[Int]) == Vector.empty[Vector[Int]]
      )
    },
    test("sets cannot enter the positional bulk API") {
      for {
        result <- typeCheck(
          "decrel.reify.bifunctor.CollectionContractSpec.Interpreter.single.applyMultiple(Set(1, 2))"
        )
      } yield assertTrue(result.isLeft)
    }
  )
}
