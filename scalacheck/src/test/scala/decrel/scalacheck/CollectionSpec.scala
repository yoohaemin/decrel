package decrel.scalacheck

import decrel.*
import decrel.scalacheck.gen.*
import org.scalacheck.{ Gen, Properties }
import org.scalacheck.Prop.forAll

object CollectionSpec extends Properties("many collection type") {
  object values extends Relation.Many[Int, Vector, Int]
  val composed = values >>: Relation.Self[Int]
  implicit val valuesProof: Proof.Many[values.type, Int, Vector, Int] =
    Gen.relationMany(values)(n => Gen.const(Vector(n, n)))
  property("preserves Vector and duplicates through composition") = forAll(Gen.choose(0, 100)) { n =>
    Gen.const(n).expand(composed).sample.contains(Vector(n, n))
  }
}
