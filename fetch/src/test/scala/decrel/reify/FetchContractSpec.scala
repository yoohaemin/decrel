package decrel.reify

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import decrel.*
import zio.test.*

object FetchContractSpec extends ZIOSpecDefault {
  object maybe  extends Relation.Optional[Int, Int]
  object values extends Relation.Many[Int, List, Int]
  object value  extends Relation.Single[Int, Int]
  class Interpreter(result: List[Int] => List[(Int, Int)])(
    protected implicit val CF: _root_.cats.effect.Concurrent[IO]
  ) extends decrel.reify.fetch[IO] {
    implicit val valueProof: Proof.Single[value.type, Int, Int] =
      implementSingleDatasource(value)(ids => IO(result(ids)))
  }
  def spec: Spec[Environment, Any] = suite("Fetch result contract")(
    test("singleton accepts exactly its requested identifier") {
      val interpreter = new Interpreter(ids => ids.map(id => id -> (id * 2)))
      import interpreter.*
      assertTrue(value.toF(3).unsafeRunSync() == 6)
    },
    test("optional, many, and custom singleton requests preserve valid result shapes") {
      val interpreter = new Interpreter(ids => ids.map(n => n -> n))
      val optional    = interpreter.implementOptionalDatasource(maybe)(ids =>
        IO.pure(ids.map(n => n -> Option.empty[Int]))
      )
      val many = interpreter.implementManyDatasource(values)(ids =>
        IO.pure(ids.map(n => n -> List.empty[Int]))
      )
      val custom = interpreter.implementCustomDatasource(value.customImpl)(ids =>
        IO.pure(ids.map(n => n -> (n * 2)))
      )
      val none   = _root_.fetch.Fetch.run(optional.reify.apply(1)).unsafeRunSync()
      val empty  = _root_.fetch.Fetch.run(many.reify.apply(1)).unsafeRunSync()
      val result = _root_.fetch.Fetch.run(custom.reify.apply(1)).unsafeRunSync()
      assertTrue(none.isEmpty, empty.isEmpty, result == 2)
    },
    test("singleton rejects empty, wrong, duplicate, and excess results") {
      val malformed = List(Nil, List(9 -> 1), List(3 -> 1, 3 -> 2), List(3 -> 1, 9 -> 2))
      val errors    = malformed.map { rows =>
        val interpreter = new Interpreter(_ => rows)
        import interpreter.*
        value.toF(3).attempt.unsafeRunSync().left.toOption
      }
      assertTrue(errors.forall(_.exists(_.getMessage.contains("Proof for relation"))))
    },
    test("batch preserves input order and duplicates on every run") {
      val interpreter = new Interpreter(ids => ids.reverse.map(id => id -> (id * 2)))
      import interpreter.*
      val run    = value.toFMany(Vector(3, 1, 3))
      val first  = run.unsafeRunSync()
      val second = run.unsafeRunSync()
      assertTrue(first == Vector(6, 2, 6), second == first)
    },
    test("batch rejects missing, duplicate, and unrequested identifiers") {
      val malformed = List(List(1 -> 2), List(1 -> 2, 1 -> 3), List(1 -> 2, 9 -> 3))
      val errors    = malformed.map { rows =>
        val interpreter = new Interpreter(_ => rows)
        import interpreter.*
        value.toFMany(List(1, 2)).attempt.unsafeRunSync().left.toOption
      }
      assertTrue(errors.forall(_.exists(_.getMessage.contains("Proof for relation"))))
    }
  )
}
