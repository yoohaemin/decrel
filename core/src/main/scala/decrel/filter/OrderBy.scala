/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter

/**
 * An inspectable lexicographic ordering, not a Scala comparator closure.
 *
 * Kept beside Predicate so that it can reuse Expr's package-private constructors
 * and Value nodes. Existing ZIO Blocks Lens -> Path conversions work unchanged.
 */
final case class OrderBy[In, Row] private[filter] (keys: Vector[OrderBy.Key]) {
  require(keys.nonEmpty, "An ordering needs at least one key")

  def thenBy(that: OrderBy[In, Row]): OrderBy[In, Row] =
    new OrderBy(keys ++ that.keys)

  def toAst: Either[FilterError, Vector[OrderBy.Key]] =
    keys.zipWithIndex
      .foldLeft[Either[FilterError, Unit]](Right(())) { case (result, (key, index)) =>
        result.flatMap { _ =>
          // Reuse the PR's structural scope checker. This synthetic IsDefined
          // is ONLY validated, never evaluated: an ordering key cannot capture
          // an OptionValue from a predicate's exists body.
          new Predicate[In, Row](Predicate.IsDefined(key.value)).toAst.left
            .map(e => e.copy(path = Vector("order", index.toString) ++ e.path))
            .map(_ => ())
        }
      }
      .map(_ => keys)

  private[decrel] def checked: OrderBy[In, Row] =
    toAst.fold(e => throw new IllegalArgumentException(e.toString), _ => this)
}

object OrderBy {
  sealed trait Direction
  case object Ascending  extends Direction
  case object Descending extends Direction

  sealed trait Missing

  /** The key is not Option-valued; missing data is a backend error. */
  case object NotOptional extends Missing
  case object NullsFirst  extends Missing
  case object NullsLast   extends Missing

  final case class Key(
    value: Predicate.Value,
    scalar: Scalar.Ordered[?],
    direction: Direction,
    missing: Missing
  )

  /** The callback runs at construction time; neither it nor any getter is kept. */
  def build[In, Row](
    f: (Expr[In, Row, In], Expr[In, Row, Row]) => OrderBy[In, Row]
  ): OrderBy[In, Row] = f(new Expr(Predicate.Input), new Expr(Predicate.Output)).checked

  private def one[In, Row, A](
    value: Predicate.Value,
    scalar: Scalar.Ordered[A],
    direction: Direction,
    missing: Missing
  ): OrderBy[In, Row] = new OrderBy(Vector(Key(value, scalar, direction, missing)))

  implicit final class ScalarOrderOps[In, Row, A](private val value: Expr[In, Row, A]) {
    def asc(implicit scalar: Scalar.Ordered[A]): OrderBy[In, Row] =
      one(value.node, scalar, Ascending, NotOptional)
    def desc(implicit scalar: Scalar.Ordered[A]): OrderBy[In, Row] =
      one(value.node, scalar, Descending, NotOptional)
  }

  /** Missing-value placement is explicit and independent of ascending/descending. */
  implicit final class OptionalOrderOps[In, Row, A](private val value: Expr[In, Row, Option[A]]) {
    def ascNullsFirst(implicit scalar: Scalar.Ordered[A]): OrderBy[In, Row] =
      one(value.node, scalar, Ascending, NullsFirst)
    def ascNullsLast(implicit scalar: Scalar.Ordered[A]): OrderBy[In, Row] =
      one(value.node, scalar, Ascending, NullsLast)
    def descNullsFirst(implicit scalar: Scalar.Ordered[A]): OrderBy[In, Row] =
      one(value.node, scalar, Descending, NullsFirst)
    def descNullsLast(implicit scalar: Scalar.Ordered[A]): OrderBy[In, Row] =
      one(value.node, scalar, Descending, NullsLast)
  }
}
