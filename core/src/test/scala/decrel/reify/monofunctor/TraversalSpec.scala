package decrel.reify.monofunctor

import zio.test.*

object TraversalSpec extends ZIOSpecDefault {
  object Deferred extends access {
    type Access[A] = () => A
    protected def succeed[A](a: A): Access[A]                               = () => a
    protected def flatMap[A, B](a: Access[A])(f: A => Access[B]): Access[B] = () => f(a())()
    protected def map[A, B](a: Access[A])(f: A => B): Access[B]             = () => f(a())
    def traverse(xs: List[Int])(f: Int => () => Int): () => List[Int]       = foreach(xs)(f)
  }
  object Branching extends access {
    type Access[A] = List[A]
    protected def succeed[A](a: A): Access[A]                               = List(a)
    protected def flatMap[A, B](a: Access[A])(f: A => Access[B]): Access[B] = a.flatMap(f)
    protected def map[A, B](a: Access[A])(f: A => B): Access[B]             = a.map(f)
    def traverse(xs: List[Int]): List[List[Int]] = foreach(xs)(n => List(n, -n))
  }
  def spec: Spec[Environment, Any] = suite("default traversal")(
    test("repeated execution allocates independent results") {
      var calls  = 0
      val run    = Deferred.traverse(List(1, 2)) { n => () => calls += 1; n + calls }
      val first  = run()
      val second = run()
      assertTrue(first == List(2, 4), second == List(4, 6), calls == 4)
    },
    test("nondeterministic branches never share an accumulator") {
      assertTrue(
        Branching.traverse(List(1, 2)) == List(List(1, 2), List(1, -2), List(-1, 2), List(-1, -2))
      )
    },
    test("empty traversal") {
      assertTrue(
        Deferred.traverse(Nil)(n => () => n)() == Nil,
        Branching.traverse(Nil) == List(Nil)
      )
    }
  )
}
