package decrel.reify

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import decrel.*
import zio.test.*

object FetchIdentitySpec extends ZIOSpecDefault {
  object value extends Relation.Single[Int, Int]
  class Interpreter(protected implicit val CF: _root_.cats.effect.Concurrent[IO])
      extends decrel.reify.fetch[IO] {
    var batches                                                 = List.empty[List[Int]]
    implicit val valueProof: Proof.Single[value.type, Int, Int] =
      implementSingleDatasource(value)(ids =>
        IO { batches = ids :: batches; ids.map(n => n -> (n * 10)) }
      )
  }
  private def runIO[A](io: IO[A]): zio.Task[A] =
    zio.ZIO.fromFuture(_ => io.unsafeToFuture())

  def spec: Spec[Environment, Any] = suite("Fetch datasource identity")(
    test("a registration batches distinct IDs and deduplicates repeated IDs") {
      val interpreter = new Interpreter
      import interpreter.*
      runIO(value.toFMany(List(1, 2, 1))).map { result =>
        assertTrue(
          result == List(10, 20, 10),
          batches.size == 1,
          batches.head.toSet == Set(1, 2),
          batches.head.size == 2
        )
      }
    },
    test("repeated requests in one query reuse the datasource cache") {
      val interpreter = new Interpreter
      import interpreter.*
      val request = value.toFetch(1)
      val monad   = _root_.fetch.fetchM[IO]
      val query   =
        monad.flatMap(request)(first => monad.map(value.toFetch(1))(second => (first, second)))
      runIO(_root_.fetch.Fetch.run(query)).map { result =>
        assertTrue(result == (10, 10), batches.size == 1)
      }
    }
  )
}
