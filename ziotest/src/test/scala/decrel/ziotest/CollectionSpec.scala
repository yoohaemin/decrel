package decrel.ziotest

import decrel.*
import decrel.ziotest.gen.*
import zio.test.*

object CollectionSpec extends ZIOSpecDefault {
  object values extends Relation.Many[Int, Vector, Int]
  val composed = values >>: Relation.Self[Int]
  implicit val valuesProof: Proof.Many[values.type, Int, Vector, Int] =
    Gen.relationMany(values)(n => Gen.const(Vector(n, n)))
  def spec: Spec[Environment, Any] = suite("many collection type")(
    test("preserves Vector and duplicates through composition") {
      check(Gen.int(0, 100)) { n =>
        check(Gen.const(n).expand(composed)) { result =>
          assertTrue(result == Vector(n, n))
        }
      }
    }
  )
}
