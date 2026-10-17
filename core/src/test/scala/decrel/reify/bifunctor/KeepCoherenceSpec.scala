package decrel.reify.bifunctor

import decrel.*
import scala.collection.IterableOps
import zio.test.*

object KeepCoherenceSpec extends ZIOSpecDefault {
  object next    extends Relation.Single[Int, Int]
  object doubled extends Relation.Single[Int, Int]

  class Interpreter extends access with proof with reifiedRelation {
    type Access[+E, +A] = () => A
    protected def succeed[A](a: A): Access[Nothing, A]                                  = () => a
    protected def flatMap[E, A, B](a: Access[E, A])(f: A => Access[E, B]): Access[E, B] = () =>
      f(a())()
    protected def map[E, A, B](a: Access[E, A])(f: A => B): Access[E, B] = () => f(a())

    var calls                                                       = 0
    def relation(f: Int => Int): ReifiedRelation[Int, Nothing, Int] =
      new ReifiedRelation.Custom[Int, Nothing, Int] {
        def apply(in: Int): Access[Nothing, Int] = () => f(in)
        def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
          in: Coll[Int]
        ): Access[Nothing, Coll[Int]] =
          () => in.map(f)
      }
    implicit val nextProof: Proof.Single[next.type, Int, Nothing, Int] =
      new Proof.Single[next.type, Int, Nothing, Int] {
        val reify = relation { _ => calls += 1; calls }
      }
    implicit val doubledProof: Proof.Single[doubled.type, Int, Nothing, Int] =
      new Proof.Single[doubled.type, Int, Nothing, Int] {
        val reify = relation(_ * 2)
      }
  }

  def spec: Spec[Environment, Any] = suite("retained single coherence")(
    test("retain exactly the value consumed downstream, once per execution") {
      val interpreter = new Interpreter
      import interpreter.*
      val composed                                           = next <>: doubled
      val reified: ReifiedRelation[Int, Nothing, (Int, Int)] = composed.reify
      val run                                                = reified.apply(0)
      val first                                              = run()
      val second                                             = run()
      assertTrue(first == (1, 2), second == (2, 4), calls == 2)
    },
    test("bulk execution retains each corresponding downstream input") {
      val interpreter = new Interpreter
      import interpreter.*
      val composed                                           = next <>: doubled
      val reified: ReifiedRelation[Int, Nothing, (Int, Int)] = composed.reify
      val result = reified.applyMultiple(Vector(0, 0, 0))()
      assertTrue(result == Vector((1, 2), (2, 4), (3, 6)), calls == 3)
    }
  )
}
