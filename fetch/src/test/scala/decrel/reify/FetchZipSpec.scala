package decrel.reify

import decrel.*
import _root_.cats.syntax.functor.*
import _root_.cats.effect.IO
import _root_.cats.effect.unsafe.implicits.global
import zio.test.*

object FetchZipSpec extends ZIOSpecDefault {
  case class Inputs(left: Int, right: Int)
  class Interpreter(protected implicit val CF: _root_.cats.effect.Concurrent[IO])
      extends decrel.reify.fetch[IO] {
    var batches = List.empty[List[Int]]
    object Lookup extends Relation.Single[Int, Int]
    object Left   extends Relation.Single[Inputs, Int]
    object Right  extends Relation.Single[Inputs, Int]
    private val source = new _root_.fetch.DataSource[IO, Int, Int] {
      implicit def CF: _root_.cats.effect.Concurrent[IO] = Interpreter.this.CF
      val data: _root_.fetch.Data[Int, Int]              = new _root_.fetch.Data[Int, Int] {
        val name = "zip-test"
      }
      def fetch(id: Int): IO[Option[Int]] = IO { batches = List(id) :: batches; Some(id * 10) }
      override def batch(ids: _root_.cats.data.NonEmptyList[Int]): IO[Map[Int, Int]] =
        IO { batches = ids.toList :: batches; ids.toList.map(n => n -> (n * 10)).toMap }
    }
    private val lookup = new Proof.Single[Lookup.type, Int, Int] {
      val reify: ReifiedRelation[Int, Int] = new ReifiedRelation.Custom[Int, Int] {
        def apply(in: Int): Access[Int] = _root_.fetch.Fetch(in, source)
        def applyMultiple[Coll[+A] <: Seq[A] & scala.collection.SeqOps[A, Coll, Coll[A]]](
          in: Coll[Int]
        ): Access[Coll[Int]] =
          _root_.fetch.Fetch
            .batchAll(in.map(apply).toSeq*)
            .map(_.to((in: scala.collection.SeqOps[Int, Coll, Coll[Int]]).iterableFactory))
      }
    }
    implicit val leftProof: Proof.Single[Left.type, Inputs, Int] =
      lookup.contramap(Left)((in: Inputs) => in.left)
    implicit val rightProof: Proof.Single[Right.type, Inputs, Int] =
      lookup.contramap(Right)((in: Inputs) => in.right)

    def pair(left: Int, right: Int): Access[(Int, Int)] =
      Proof
        .composedZippedProof[
          Left.type,
          Inputs,
          Int,
          Int,
          Right.type,
          Inputs,
          Int,
          (Int, Int),
          (Int, Int)
        ]
        .reify
        .apply(Inputs(left, right))
  }

  private def runIO[A](io: IO[A]): zio.Task[A] =
    zio.ZIO.fromFuture(_ => io.unsafeToFuture())

  def spec: Spec[Environment, Any] = suite("Fetch zip through foreach")(
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
