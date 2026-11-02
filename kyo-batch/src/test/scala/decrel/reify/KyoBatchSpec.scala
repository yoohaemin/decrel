package decrel.reify

import decrel.*
import kyo.*
import zio.test.*

object KyoBatchSpec extends ZIOSpecDefault {
  object Single   extends Relation.Single[Int, Int]
  object Optional extends Relation.Optional[Int, Int]
  object Many     extends Relation.Many[Int, List, Int]

  def spec = suite("Kyo batch regression")(
    test("composed optional relations batch the present values") {
      val interpreter = new kyoBatch[Any] {}
      import interpreter.*
      var calls = List.empty[Seq[Int]]
      implicit val optional: Proof.Optional[Optional.type & Relation.Optional[Int, Int], Int, Int] =
        implementOptionalDatasource(Optional) { inputs =>
          inputs.map(n => n -> Option.when(n > 0)(n)).toMap
        }
      implicit val single: Proof.Single[Single.type & Relation.Single[Int, Int], Int, Int] =
        implementSingleDatasource(Single) { inputs =>
          calls = calls :+ inputs
          inputs.map(n => n -> (n * 10)).toMap
        }
      val result = (Optional >>: Single).startingFromMany(Vector(2, 0, 1, 2)).eval
      assertTrue(
        result == Vector(Some(20), None, Some(10), Some(20)),
        calls.size == 1,
        calls.head.toSet == Set(1, 2)
      )
    },
    test("single datasource batches inputs and preserves duplicates and order") {
      val interpreter = new kyoBatch[Any] {}
      var calls       = List.empty[Seq[Int]]
      val proof       = interpreter.implementSingleDatasource(Single) { inputs =>
        calls = calls :+ inputs
        inputs.map(n => n -> (n * 10)).toMap
      }
      val result: Vector[Int] = proof.reify.applyMultiple(Vector(3, 1, 3)).eval
      assertTrue(result == Vector(30, 10, 30), calls.size == 1, calls.head.toSet == Set(1, 3))
    },
    test("optional and many datasources preserve missing and empty results") {
      val interpreter = new kyoBatch[Any] {}
      val optional    = interpreter.implementOptionalDatasource(Optional) { inputs =>
        inputs.map(n => n -> Option.when(n > 0)(n * 10)).toMap
      }
      val many = interpreter.implementManyDatasource(Many) { inputs =>
        inputs.map(n => n -> List.fill(n)(n)).toMap
      }
      assertTrue(
        optional.reify.applyMultiple(List(1, 0, 1)).eval == List(Some(10), None, Some(10)),
        many.reify.applyMultiple(Vector(2, 0, 1)).eval == Vector(List(2, 2), Nil, List(1)),
        optional.reify.apply(0).eval == None,
        many.reify.apply(0).eval == Nil,
        many.reify.applyMultiple(List.empty[Int]).eval == Nil
      )
    },
    test("contramapped proofs retain optional and many collection shapes") {
      val interpreter = new kyoBatch[Any] {}
      val proof       = interpreter.implementSingleDatasource(Single) { inputs =>
        inputs.map(n => n -> (n * 10)).toMap
      }
      val optional =
        interpreter.contramapOptionalProof(proof, Optional, (n: Int) => Option.when(n > 0)(n))
      val many = interpreter.contramapManyProof(proof, Many, (n: Int) => List.fill(n)(n))
      assertTrue(
        optional.reify.applyMultiple(Vector(1, 0, 2)).eval == Vector(Some(10), None, Some(20)),
        many.reify.applyMultiple(List(2, 0, 1)).eval == List(List(20, 20), Nil, List(10))
      )
    }
  )
}
