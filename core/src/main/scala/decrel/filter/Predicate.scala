/*
 * Copyright (c) 2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter

/** An inspectable predicate over an edge's input and one candidate output. */
final class Predicate[In, Row] private[filter] (private[filter] val node: Predicate.Test) {
  def &&(that: Predicate[In, Row]): Predicate[In, Row] =
    new Predicate(Predicate.And(node, that.node))

  def ||(that: Predicate[In, Row]): Predicate[In, Row] =
    new Predicate(Predicate.Or(node, that.node))

  def unary_! : Predicate[In, Row] = new Predicate(Predicate.Not(node))

  /** Inspect the closed tree, rejecting an Option value used outside its exists scope. */
  def toAst: Either[FilterError, Predicate.Test] = Predicate.validate(node).map(_ => node)

  private[decrel] def checked: Predicate[In, Row] =
    toAst.fold(error => throw new IllegalArgumentException(error.toString), _ => this)

  override def equals(other: Any): Boolean = other match {
    case that: Predicate[?, ?] => node == that.node
    case _                     => false
  }

  override def hashCode(): Int  = node.hashCode()
  override def toString: String = s"Predicate($node)"
}

object Predicate {
  sealed trait Value
  case object Input                                             extends Value
  case object Output                                            extends Value
  final case class Field(source: Value, fields: Vector[String]) extends Value
  final case class Literal(value: Any, scalar: Scalar[?])       extends Value {
    // Literal identity is structural, not the predicate language's numeric equality.
    // Canonical NaN bits allow independently constructed trees to share cache keys.
    private def identityValue: Any = value match {
      case n: Float  => java.lang.Float.floatToIntBits(n)
      case n: Double => java.lang.Double.doubleToLongBits(n)
      case other     => other
    }

    override def equals(other: Any): Boolean = other match {
      case that: Literal => scalar == that.scalar && identityValue == that.identityValue
      case _             => false
    }
    override def hashCode(): Int = (scalar, identityValue).hashCode()
  }

  /** The value of source, available only within Exists(source, ...). */
  final case class OptionValue(source: Value) extends Value

  sealed trait Comparison
  object Comparison {
    case object Equal              extends Comparison
    case object NotEqual           extends Comparison
    case object LessThan           extends Comparison
    case object LessThanOrEqual    extends Comparison
    case object GreaterThan        extends Comparison
    case object GreaterThanOrEqual extends Comparison
  }

  sealed trait Test
  final case class Constant(value: Boolean) extends Test
  final case class Compare(left: Value, operator: Comparison, right: Value, scalar: Scalar[?])
      extends Test
  final case class And(left: Test, right: Test) extends Test
  final case class Or(left: Test, right: Test)  extends Test
  final case class Not(value: Test)             extends Test
  final case class IsDefined(value: Value)      extends Test

  /** False for None; otherwise evaluate body with OptionValue(value) bound to its payload. */
  final case class Exists(value: Value, body: Test) extends Test

  def always[In, Row]: Predicate[In, Row] = new Predicate(Constant(true))
  def never[In, Row]: Predicate[In, Row]  = new Predicate(Constant(false))

  /** The callback constructs a tree once; it is not retained or run against records. */
  def build[In, Row](
    f: (Expr[In, Row, In], Expr[In, Row, Row]) => Predicate[In, Row]
  ): Predicate[In, Row] = f(new Expr(Input), new Expr(Output)).checked

  private def validate(tree: Test): Either[FilterError, Unit] = {
    def value(node: Value, scope: Set[Value], path: Vector[String]): Either[FilterError, Unit] =
      node match {
        case Input | Output | Literal(_, _) => Right(())
        case Field(source, _)               => value(source, scope, path :+ "source")
        case OptionValue(source)            =>
          if (scope.contains(source)) value(source, scope, path :+ "source")
          else Left(FilterError(path, "Option value escaped its exists scope"))
      }

    def test(node: Test, scope: Set[Value], path: Vector[String]): Either[FilterError, Unit] =
      node match {
        case Constant(_)                => Right(())
        case Compare(left, _, right, _) =>
          value(left, scope, path :+ "left").flatMap(_ => value(right, scope, path :+ "right"))
        case And(left, right) =>
          test(left, scope, path :+ "left").flatMap(_ => test(right, scope, path :+ "right"))
        case Or(left, right) =>
          test(left, scope, path :+ "left").flatMap(_ => test(right, scope, path :+ "right"))
        case Not(inner)           => test(inner, scope, path :+ "not")
        case IsDefined(inner)     => value(inner, scope, path :+ "value")
        case Exists(source, body) =>
          value(source, scope, path :+ "value").flatMap(_ =>
            test(body, scope + source, path :+ "body")
          )
      }

    test(tree, Set.empty, Vector.empty)
  }
}

/** A typed value expression. Constructors are kept inside the language and its adapters. */
final class Expr[In, Row, A] private[filter] (private[filter] val node: Predicate.Value) {
  import Predicate.Comparison

  def apply[B](path: Path[A, B]): Expr[In, Row, B] = {
    val selected = node match {
      case Predicate.Field(source, fields) => Predicate.Field(source, fields ++ path.fields)
      case _                               => Predicate.Field(node, path.fields)
    }
    new Expr(selected)
  }

  private def compare(
    that: Expr[In, Row, A],
    operator: Comparison,
    scalar: Scalar[A]
  ): Predicate[In, Row] =
    new Predicate(Predicate.Compare(node, operator, that.node, scalar))

  def ===(that: Expr[In, Row, A])(implicit scalar: Scalar[A]): Predicate[In, Row] =
    compare(that, Comparison.Equal, scalar)
  def ===(that: A)(implicit scalar: Scalar[A]): Predicate[In, Row] =
    this === Expr.literal[In, Row, A](that)
  def !==(that: Expr[In, Row, A])(implicit scalar: Scalar[A]): Predicate[In, Row] =
    compare(that, Comparison.NotEqual, scalar)
  def !==(that: A)(implicit scalar: Scalar[A]): Predicate[In, Row] =
    this !== Expr.literal[In, Row, A](that)
  def <(that: Expr[In, Row, A])(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    compare(that, Comparison.LessThan, scalar)
  def <(that: A)(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    this < Expr.literal[In, Row, A](that)
  def <=(that: Expr[In, Row, A])(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    compare(that, Comparison.LessThanOrEqual, scalar)
  def <=(that: A)(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    this <= Expr.literal[In, Row, A](that)
  def >(that: Expr[In, Row, A])(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    compare(that, Comparison.GreaterThan, scalar)
  def >(that: A)(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    this > Expr.literal[In, Row, A](that)
  def >=(that: Expr[In, Row, A])(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    compare(that, Comparison.GreaterThanOrEqual, scalar)
  def >=(that: A)(implicit scalar: Scalar.Ordered[A]): Predicate[In, Row] =
    this >= Expr.literal[In, Row, A](that)
}

object Expr {
  def literal[In, Row, A](value: A)(implicit scalar: Scalar[A]): Expr[In, Row, A] = {
    require(value != null, "Filter literals cannot be null; use Option predicates")
    new Expr(Predicate.Literal(value, scalar))
  }

  implicit final class OptionOps[In, Row, A](private val self: Expr[In, Row, Option[A]])
      extends AnyVal {
    def isDefined: Predicate[In, Row] = new Predicate(Predicate.IsDefined(self.node))
    def isEmpty: Predicate[In, Row]   = !isDefined
    def exists(f: Expr[In, Row, A] => Predicate[In, Row]): Predicate[In, Row] =
      new Predicate(Predicate.Exists(self.node, f(new Expr(Predicate.OptionValue(self.node))).node))
  }
}
