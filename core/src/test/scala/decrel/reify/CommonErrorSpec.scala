/*
 * Copyright (c) Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import decrel.reify.either.*
import scala.collection.IterableOps
import zio.test.*

object CommonErrorSpec extends ZIOSpecDefault {
  sealed trait Failure
  case object LeftFailure  extends Failure
  case object RightFailure extends Failure

  final case class Input(value: Int)
  final case class Middle(value: Int)
  final case class Output(value: Int)

  case object LeftSingle   extends Relation.Single[Input, Middle]
  case object LeftOptional extends Relation.Optional[Input, Middle]
  case object LeftMany     extends Relation.Many[Input, Vector, Middle]
  case object RightSingle  extends Relation.Single[Middle, Output]
  case object RightZipped  extends Relation.Single[Input, Output]

  private def interpreter[In, E, Out](f: In => Either[E, Out]): ReifiedRelation[In, E, Out] =
    new ReifiedRelation.Custom[In, E, Out] {
      override def apply(in: In): Either[E, Out] = f(in)

      override def applyMultiple[CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]]](
        in: CC[In]
      ): Either[E, CC[Out]] =
        in.foldLeft[Either[E, Vector[Out]]](Right(Vector.empty)) { (acc, value) =>
          acc.flatMap(values => f(value).map(values :+ _))
        }.map(_.to((in: IterableOps[In, CC, CC[In]]).iterableFactory))
    }

  implicit val leftSingleProof: Proof.Single[LeftSingle.type, Input, LeftFailure.type, Middle] =
    new Proof.Single[LeftSingle.type, Input, LeftFailure.type, Middle] {
      override def apply(
        relationValue: LeftSingle.type
      ): ReifiedRelation[Input, LeftFailure.type, Middle] = cachedInterpreter

      private val cachedInterpreter: ReifiedRelation[Input, LeftFailure.type, Middle] =
        interpreter((in: Input) =>
          if (in.value == 0) Left(LeftFailure) else Right(Middle(in.value))
        )
    }

  implicit val leftOptionalProof
    : Proof.Optional[LeftOptional.type, Input, LeftFailure.type, Middle] =
    new Proof.Optional[LeftOptional.type, Input, LeftFailure.type, Middle] {
      override def apply(
        relationValue: LeftOptional.type
      ): ReifiedRelation[Input, LeftFailure.type, Option[Middle]] = cachedInterpreter

      private val cachedInterpreter: ReifiedRelation[Input, LeftFailure.type, Option[Middle]] =
        interpreter((in: Input) =>
          if (in.value == 0) Left(LeftFailure)
          else Right(Option.when(in.value >= 0)(Middle(in.value)))
        )
    }

  implicit val leftManyProof: Proof.Many[LeftMany.type, Input, LeftFailure.type, Vector, Middle] =
    new Proof.Many[LeftMany.type, Input, LeftFailure.type, Vector, Middle] {
      override def apply(
        relationValue: LeftMany.type
      ): ReifiedRelation[Input, LeftFailure.type, Vector[Middle]] = cachedInterpreter

      private val cachedInterpreter: ReifiedRelation[Input, LeftFailure.type, Vector[Middle]] =
        interpreter((in: Input) =>
          if (in.value == 0) Left(LeftFailure)
          else
            Right(
              if (in.value < 0) Vector.empty else Vector(Middle(in.value), Middle(in.value + 1))
            )
        )
    }

  implicit val rightSingleProof: Proof.Single[RightSingle.type, Middle, RightFailure.type, Output] =
    new Proof.Single[RightSingle.type, Middle, RightFailure.type, Output] {
      override def apply(
        relationValue: RightSingle.type
      ): ReifiedRelation[Middle, RightFailure.type, Output] = cachedInterpreter

      private val cachedInterpreter: ReifiedRelation[Middle, RightFailure.type, Output] =
        interpreter((in: Middle) =>
          if (in.value == 1) Left(RightFailure) else Right(Output(in.value + 10))
        )
    }

  implicit val rightZippedProof: Proof.Single[RightZipped.type, Input, RightFailure.type, Output] =
    rightSingleProof.contramap[RightZipped.type, Input](RightSingle, RightZipped)(in =>
      Middle(in.value)
    )

  private def join[A, B, E](left: A, right: B)(implicit errors: CommonError[A, B, E]): (E, E) =
    (errors.left(left), errors.right(right))

  override def spec: Spec[Environment, Any] = suite("common error")(
    test("an infallible child keeps the other child's error type") {
      val leftInfallible  = implicitly[CommonError[Nothing, Int, Int]]
      val rightInfallible = implicitly[CommonError[Int, Nothing, Int]]
      assertTrue(leftInfallible.right(7) == 7, rightInfallible.left(8) == 8)
    },
    test("a common supertype works in either child order") {
      val leftWider  = implicitly[CommonError[AnyVal, Int, AnyVal]]
      val rightWider = implicitly[CommonError[Int, AnyVal, AnyVal]]
      assertTrue(
        leftWider.left(true) == true,
        leftWider.right(7) == 7,
        rightWider.left(8) == 8,
        rightWider.right(false) == false
      )
    },
    test("independent child types infer a shared error without changing either value") {
      val inferred                   = join(LeftFailure, RightFailure)
      val errors: (Failure, Failure) = inferred
      assertTrue(errors._1 eq LeftFailure, errors._2 eq RightFailure)
    },
    test("single composition infers a shared error and preserves failures from both children") {
      val relation                                         = LeftSingle >>: RightSingle
      val inferred                                         = relation.reify
      val reified: ReifiedRelation[Input, Failure, Output] = inferred
      assertTrue(
        reified(Input(0)) == Left(LeftFailure),
        reified(Input(1)) == Left(RightFailure),
        reified(Input(2)) == Right(Output(12)),
        reified.applyMultiple(Vector(Input(2), Input(0))) == Left(LeftFailure),
        reified.applyMultiple(Vector(Input(2), Input(1))) == Left(RightFailure)
      )
    },
    test("optional composition preserves child failures and an absent result") {
      val relation                                                 = LeftOptional >>: RightSingle
      val inferred                                                 = relation.reify
      val reified: ReifiedRelation[Input, Failure, Option[Output]] = inferred
      assertTrue(
        reified(Input(0)) == Left(LeftFailure),
        reified(Input(1)) == Left(RightFailure),
        reified(Input(-1)) == Right(None),
        reified(Input(2)) == Right(Some(Output(12))),
        reified.applyMultiple(Vector(Input(-1), Input(0))) == Left(LeftFailure),
        reified.applyMultiple(Vector(Input(-1), Input(1))) == Left(RightFailure)
      )
    },
    test("many composition preserves child failures and collection results") {
      val relation                                                 = LeftMany >>: RightSingle
      val inferred                                                 = relation.reify
      val reified: ReifiedRelation[Input, Failure, Vector[Output]] = inferred
      assertTrue(
        reified(Input(0)) == Left(LeftFailure),
        reified(Input(1)) == Left(RightFailure),
        reified(Input(-1)) == Right(Vector.empty),
        reified(Input(2)) == Right(Vector(Output(12), Output(13))),
        reified.applyMultiple(Vector(Input(2), Input(0))) == Left(LeftFailure),
        reified.applyMultiple(Vector(Input(2), Input(1))) == Left(RightFailure)
      )
    },
    test("zipped proofs preserve independent child failures and both outputs") {
      val relation                                                   = LeftSingle.zip(RightZipped)
      val inferred                                                   = relation.reify
      val reified: ReifiedRelation[Input, Failure, (Middle, Output)] = inferred
      assertTrue(
        reified(Input(0)) == Left(LeftFailure),
        reified(Input(1)) == Left(RightFailure),
        reified(Input(2)) == Right((Middle(2), Output(12))),
        reified.applyMultiple(Vector(Input(2), Input(0))) == Left(LeftFailure),
        reified.applyMultiple(Vector(Input(2), Input(1))) == Left(RightFailure)
      )
    }
  )
}
