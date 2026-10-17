package decrel.reify

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import zio.test.*

object FetchZipSpec extends ZIOSpecDefault {
  class Interpreter(protected implicit val CF: _root_.cats.effect.Concurrent[IO])
      extends decrel.reify.fetch[IO] {
    var batches        = List.empty[List[Int]]
    private val source = new _root_.fetch.DataSource[IO, Int, Int] {
      implicit def CF: _root_.cats.effect.Concurrent[IO] = Interpreter.this.CF
      val data: _root_.fetch.Data[Int, Int]              = new _root_.fetch.Data[Int, Int] {
        val name = "zip-test"
      }
      def fetch(id: Int): IO[Option[Int]] = IO { batches = List(id) :: batches; Some(id * 10) }
      override def batch(ids: _root_.cats.data.NonEmptyList[Int]): IO[Map[Int, Int]] =
        IO { batches = ids.toList :: batches; ids.toList.map(n => n -> (n * 10)).toMap }
    }
    def pair(left: Int, right: Int): Access[(Int, Int)] =
      zipWith(_root_.fetch.Fetch(left, source), _root_.fetch.Fetch(right, source))((_, _))
  }

  private def runIO[A](io: IO[A]): zio.Task[A] =
    zio.ZIO.fromFuture(_ => io.unsafeToFuture())

  def spec: Spec[Environment, Any] = suite("typed Fetch zip")(
    test("independent requests to one datasource share a batch") {
      val interpreter = new Interpreter
      // Let the Scala.js test runner attach its completion listener before executing the query.
      Live.live(zio.ZIO.sleep(zio.Duration.fromMillis(1))) *> runIO(
        _root_.fetch.Fetch.run(interpreter.pair(1, 2))
      ).map { result =>
        assertTrue(
          result == (10, 20),
          interpreter.batches.size == 1,
          interpreter.batches.head.toSet == Set(1, 2)
        )
      }
    }
  )
}
