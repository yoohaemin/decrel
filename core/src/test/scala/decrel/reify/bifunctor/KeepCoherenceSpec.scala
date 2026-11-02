package decrel.reify.bifunctor

import decrel.*
import zio.test.*

object KeepCoherenceSpec extends ZIOSpecDefault {
  object next    extends Relation.Single[Int, Int]
  object doubled extends Relation.Single[Int, Int]

  object described extends Relation.Single[AnyVal, String]

  class Interpreter extends access with proof with reifiedRelation {
    type Access[+E, +A] = () => A
    protected def succeed[A](a: A): Access[Nothing, A]                                  = () => a
    protected def flatMap[E, A, B](a: Access[E, A])(f: A => Access[E, B]): Access[E, B] = () =>
      f(a())()
    protected def map[E, A, B](a: Access[E, A])(f: A => B): Access[E, B] = () => f(a())

    implicit val describedProof: Proof.Single[described.type, AnyVal, Nothing, String] =
      new Proof.Single[described.type, AnyVal, Nothing, String] {
        val reify: ReifiedRelation[AnyVal, Nothing, String] =
          new ReifiedRelation.FromFunction[AnyVal, String](_.toString)
      }

    var calls = 0
    type Id[A] = A
    def relation(f: Int => Int): ReifiedRelation[Int, Nothing, Int] =
      new ReifiedRelation.Transformed[Int, Id, Nothing, Id, Int](
        new ReifiedRelation.FromFunction[Int, Int](identity),
        f
      )
    implicit val nextProof: Proof.Single[next.type, Int, Nothing, Int] =
      new Proof.Single[next.type, Int, Nothing, Int] {
        val reify = relation { _ => calls += 1; calls }
      }
    implicit val doubledProof: Proof.Single[doubled.type, Int, Nothing, Int] =
      new Proof.Single[doubled.type, Int, Nothing, Int] {
        val reify = relation(_ * 2)
      }
  }

  private def checked(label: String)(assertion: => TestResult) =
    test(label)(Live.live(zio.ZIO.sleep(zio.Duration.fromMillis(10))) *> zio.ZIO.attempt(assertion))

  def spec: Spec[Environment, Any] = suite("retained single coherence")(
    checked("retained values can feed a strict supertype input") {
      val interpreter = new Interpreter
      import interpreter.*
      val composed = next <>: described
      val reified  = composed.reify
      val single   = reified.apply(0)()
      val bulk     = reified.applyMultiple(Vector(0, 0))()
      assertTrue(single == (1, "1"), bulk == Vector((2, "2"), (3, "3")), calls == 3)
    },
    checked("retain exactly the value consumed downstream, once per execution") {
      val interpreter = new Interpreter
      import interpreter.*
      val composed                                           = next <>: doubled
      val reified: ReifiedRelation[Int, Nothing, (Int, Int)] = composed.reify
      val run                                                = reified.apply(0)
      val first                                              = run()
      val second                                             = run()
      assertTrue(first == (1, 2), second == (2, 4), calls == 2)
    },
    checked("bulk execution retains each corresponding downstream input") {
      val interpreter = new Interpreter
      import interpreter.*
      val composed                                           = next <>: doubled
      val reified: ReifiedRelation[Int, Nothing, (Int, Int)] = composed.reify
      val result = reified.applyMultiple(Vector(0, 0, 0))()
      assertTrue(result == Vector((1, 2), (2, 4), (3, 6)), calls == 3)
    }
  )
}
