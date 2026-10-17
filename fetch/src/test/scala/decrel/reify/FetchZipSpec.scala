package decrel.reify

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import decrel.*
import zio.test.*
import scala.collection.IterableOps

object FetchZipSpec extends ZIOSpecDefault {
  object value extends Relation.Single[Int, Int]
  object next  extends Relation.Single[Int, Int]
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
    implicit val valueProof: Proof.Single[value.type, Int, Int] =
      new Proof.Single[value.type, Int, Int] {
        val reify: ReifiedRelation[Int, Int] = new ReifiedRelation.Custom[Int, Int] {
          def apply(in: Int): Access[Int] = _root_.fetch.Fetch(in, source)
          def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[Int]
          ): Access[Coll[Int]] =
            foreach(in)(apply)
        }
      }
    implicit val nextProof: Proof.Single[next.type, Int, Int] =
      new Proof.Single[next.type, Int, Int] {
        val reify: ReifiedRelation[Int, Int] = new ReifiedRelation.Custom[Int, Int] {
          def apply(in: Int): Access[Int] = valueProof.reify.apply(in + 1)
          def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
            in: Coll[Int]
          ): Access[Coll[Int]] =
            valueProof.reify.applyMultiple(in.map(_ + 1))
        }
      }
  }
  def spec: Spec[Environment, Any] = suite("typed Fetch zip")(
    test("independent requests to one datasource share a batch") {
      val interpreter = new Interpreter
      import interpreter.*
      val query  = value & next
      val result = query.toF(1).unsafeRunSync()
      assertTrue(result == (10, 20), batches.size == 1, batches.head.toSet == Set(1, 2))
    }
  )
}
