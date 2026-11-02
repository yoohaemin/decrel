package decrel.reify

import kyo.*
import zio.test.*

object KyoGenericSpec extends ZIOSpecDefault {
  object Pure extends kyoGeneric[Any] {
    def increment(inputs: Vector[Int]): Vector[Int] < Any = foreach(inputs)(n => n + 1)
    def empty: List[Int] < Any                            = foreach(List.empty[Int])(n => n + 1)
  }
  object Branching extends kyoGeneric[Choice] {
    def signed(inputs: List[Int]): List[Int] < Choice = foreach(inputs)(n => Choice.eval(n, -n))
  }

  def spec = suite("Kyo generic regression")(
    test("multiple inputs retain order, duplicates and collection type") {
      val result: Vector[Int] = Pure.increment(Vector(3, 1, 3)).eval
      assertTrue(
        result == Vector(4, 2, 4),
        Pure.empty.eval == Nil
      )
    },
    test("branching traversal keeps independent accumulators across repeated runs") {
      val computation = Branching.signed(List(1, 2))
      val expected    = Seq(List(1, 2), List(1, -2), List(-1, 2), List(-1, -2))
      assertTrue(
        Choice.run(computation).eval.toSeq == expected,
        Choice.run(computation).eval.toSeq == expected
      )
    }
  )
}
