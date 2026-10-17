package decrel.reify

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import decrel.*
import zio.test.*

object FetchInitializationSpec extends ZIOSpecDefault {
  object value      extends Relation.Single[Int, Int]
  class Interpreter extends decrel.reify.fetch[IO] {
    protected implicit val CF: _root_.cats.effect.Concurrent[IO] = IO.asyncForIO
    implicit val valueProof: Proof.Single[value.type, Int, Int]  =
      implementSingleDatasource(value)(ids => IO.pure(ids.map(n => n -> (n * 10))))
  }
  def spec: Spec[Environment, Any] = suite("Fetch initialization")(
    test("ordinary val Concurrent initializes before the Fetch Monad") {
      val interpreter = new Interpreter
      import interpreter.*
      val one  = value.toF(1).unsafeRunSync()
      val many = value.toFMany(List(1, 2)).unsafeRunSync()
      assertTrue(one == 10, many == List(10, 20))
    }
  )
}
