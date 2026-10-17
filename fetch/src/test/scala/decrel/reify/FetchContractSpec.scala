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
  private def runIO[A](io: IO[A]): zio.Task[A] =
    zio.ZIO.fromFuture(_ => io.unsafeToFuture())

  def spec: Spec[Environment, Any] = suite("Fetch result contract")(
    test("singleton accepts exactly its requested identifier") {
      val interpreter = new Interpreter(ids => ids.map(id => id -> (id * 2)))
      import interpreter.*
      runIO(value.toF(3)).map(result => assertTrue(result == 6))
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
      for {
        none   <- runIO(_root_.fetch.Fetch.run(optional.reify.apply(1)))
        empty  <- runIO(_root_.fetch.Fetch.run(many.reify.apply(1)))
        result <- runIO(_root_.fetch.Fetch.run(custom.reify.apply(1)))
      } yield assertTrue(none.isEmpty, empty.isEmpty, result == 2)
    },
    test("singleton rejects empty, wrong, duplicate, and excess results") {
      val malformed = List(Nil, List(9 -> 1), List(3 -> 1, 3 -> 2), List(3 -> 1, 9 -> 2))
      zio.ZIO
        .foreach(malformed) { rows =>
          val interpreter = new Interpreter(_ => rows)
          import interpreter.*
          runIO(value.toF(3).attempt).map(_.left.toOption)
        }
        .map(errors =>
          assertTrue(errors.forall(_.exists(_.getMessage.contains("Proof for relation"))))
        )
    },
    test("batch preserves input order and duplicates on every run") {
      val interpreter = new Interpreter(ids => ids.reverse.map(id => id -> (id * 2)))
      import interpreter.*
      val run = value.toFMany(Vector(3, 1, 3))
      for {
        first  <- runIO(run)
        second <- runIO(run)
      } yield assertTrue(first == Vector(6, 2, 6), second == first)
    },
    test("batch rejects missing, duplicate, and unrequested identifiers") {
      val malformed = List(List(1 -> 2), List(1 -> 2, 1 -> 3), List(1 -> 2, 9 -> 3))
      zio.ZIO
        .foreach(malformed) { rows =>
          val interpreter = new Interpreter(_ => rows)
          import interpreter.*
          runIO(value.toFMany(List(1, 2)).attempt).map(_.left.toOption)
        }
        .map(errors =>
          assertTrue(errors.forall(_.exists(_.getMessage.contains("Proof for relation"))))
        )
    }
  )
}
