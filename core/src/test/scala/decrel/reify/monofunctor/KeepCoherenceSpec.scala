package decrel.reify.monofunctor

import decrel.*
import zio.test.*

object KeepCoherenceSpec extends ZIOSpecDefault {
  object next    extends Relation.Single[Int, Int]
  object doubled extends Relation.Single[Int, Int]

  class Interpreter extends access with proof with reifiedRelation {
    type Access[A] = () => A
    protected def succeed[A](a: A): Access[A]                               = () => a
    protected def flatMap[A, B](a: Access[A])(f: A => Access[B]): Access[B] = () => f(a())()
    protected def map[A, B](a: Access[A])(f: A => B): Access[B]             = () => f(a())

    var calls = 0
    type Id[A] = A
    def relation(f: Int => Int): ReifiedRelation[Int, Int] =
      new ReifiedRelation.Transformed[Int, Id, Id, Int](
        new ReifiedRelation.FromFunction[Int, Int](identity),
        f
      )
    implicit val nextProof: Proof.Single[next.type, Int, Int] =
      new Proof.Single[next.type, Int, Int] {
        val reify = relation { _ => calls += 1; calls }
      }
    implicit val doubledProof: Proof.Single[doubled.type, Int, Int] =
      new Proof.Single[doubled.type, Int, Int] {
        val reify = relation(_ * 2)
      }
  }

  def spec: Spec[Environment, Any] = suite("retained single coherence")(
    test("retain exactly the value consumed downstream, once per execution") {
      val interpreter = new Interpreter
      import interpreter.*
      val composed                                  = next <>: doubled
      val reified: ReifiedRelation[Int, (Int, Int)] = composed.reify
      val run                                       = reified.apply(0)
      val first                                     = run()
      val second                                    = run()
      assertTrue(first == (1, 2), second == (2, 4), calls == 2)
    },
    test("bulk execution retains each corresponding downstream input") {
      val interpreter = new Interpreter
      import interpreter.*
      val composed                                  = next <>: doubled
      val reified: ReifiedRelation[Int, (Int, Int)] = composed.reify
      val result                                    = reified.applyMultiple(Vector(0, 0, 0))()
      assertTrue(result == Vector((1, 2), (2, 4), (3, 6)), calls == 3)
    }
  )
}
