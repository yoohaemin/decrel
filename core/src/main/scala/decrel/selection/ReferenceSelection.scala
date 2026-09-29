/* SPDX-License-Identifier: MPL-2.0 */
package decrel.selection

import decrel.filter.{ FilterError, OrderBy, Predicate, Scalar }
import scala.util.control.NonFatal

/**
 * A deliberately small, real reference compiler/evaluator.
 *
 * The backend supplies field-to-value mappings, just as a SQL backend would
 * supply field-to-column mappings. Getters are NOT stored in the query AST.
 * Unsupported semantics fail explicitly. Floating-point comparisons/order are
 * intentionally unsupported here: this prototype does not invent NaN rules.
 */
final class ReferenceSelection(
  readField: (Any, String) => Either[FilterError, Any]
) extends SelectionCompiler[ReferenceSelection.Program] {
  import ReferenceSelection.*

  override protected def compileAst(ast: Selection.Ast): Either[FilterError, Program] =
    protect {
      ast.predicate.foreach(checkPredicate(_, Vector("filter")))
      ast.ordering.zipWithIndex.foreach { case (key, index) =>
        checkScalar(key.scalar, Vector("order", index.toString))
      }
      new Program(ast, readField)
    }
}

object ReferenceSelection {
  private final case class Invalid(error: FilterError) extends RuntimeException(error.toString)

  private def fail(path: Vector[String], reason: String): Nothing =
    throw Invalid(FilterError(path, reason))

  private def protect[A](body: => A): Either[FilterError, A] =
    try Right(body)
    catch {
      case Invalid(error)  => Left(error)
      case NonFatal(error) =>
        Left(
          FilterError(
            Vector("reference"),
            s"Invalid backend field value: ${Option(error.getMessage).getOrElse(error.getClass.getName)}"
          )
        )
    }

  private def checkScalar(scalar: Scalar[?], path: Vector[String]): Unit = scalar match {
    case Scalar.FloatType | Scalar.DoubleType =>
      fail(path, "The reference backend does not support floating-point semantics")
    case _ => ()
  }

  private def checkPredicate(test: Predicate.Test, path: Vector[String]): Unit = test match {
    case Predicate.Constant(_) | Predicate.IsDefined(_) => ()
    case Predicate.Compare(_, operator, _, scalar)      =>
      checkScalar(scalar, path)
      if (
        scalar == Scalar.BooleanType &&
        operator != Predicate.Comparison.Equal && operator != Predicate.Comparison.NotEqual
      )
        fail(path, "Boolean values cannot be ordered")
    case Predicate.And(a, b) =>
      checkPredicate(a, path :+ "left"); checkPredicate(b, path :+ "right")
    case Predicate.Or(a, b) =>
      checkPredicate(a, path :+ "left"); checkPredicate(b, path :+ "right")
    case Predicate.Not(a)          => checkPredicate(a, path :+ "not")
    case Predicate.Exists(_, body) => checkPredicate(body, path :+ "body")
  }

  // Finite/integer/decimal/string comparisons follow the PR's scalar domains.
  // Float and Double are rejected before execution, including equality tests.
  private def compare(scalar: Scalar[?], left: Any, right: Any): Int = scalar match {
    case Scalar.BooleanType =>
      java.lang.Boolean.compare(left.asInstanceOf[Boolean], right.asInstanceOf[Boolean])
    case Scalar.ByteType =>
      java.lang.Byte.compare(left.asInstanceOf[Byte], right.asInstanceOf[Byte])
    case Scalar.ShortType =>
      java.lang.Short.compare(left.asInstanceOf[Short], right.asInstanceOf[Short])
    case Scalar.IntType =>
      java.lang.Integer.compare(left.asInstanceOf[Int], right.asInstanceOf[Int])
    case Scalar.LongType =>
      java.lang.Long.compare(left.asInstanceOf[Long], right.asInstanceOf[Long])
    case Scalar.CharType =>
      java.lang.Character.compare(left.asInstanceOf[Char], right.asInstanceOf[Char])
    case Scalar.StringType     => left.asInstanceOf[String].compareTo(right.asInstanceOf[String])
    case Scalar.BigIntType     => left.asInstanceOf[BigInt].compare(right.asInstanceOf[BigInt])
    case Scalar.BigDecimalType =>
      left.asInstanceOf[BigDecimal].compare(right.asInstanceOf[BigDecimal])
    case _ => fail(Vector("scalar"), "Unsupported scalar")
  }

  final class Program private[selection] (
    val ast: Selection.Ast,
    readField: (Any, String) => Either[FilterError, Any]
  ) {
    private def value(node: Predicate.Value, in: Any, row: Any): Any = node match {
      case Predicate.Input                 => in
      case Predicate.Output                => row
      case Predicate.Literal(v, _)         => v
      case Predicate.Field(source, fields) =>
        fields.foldLeft(value(source, in, row)) { (record, field) =>
          readField(record, field).fold(e => throw Invalid(e), identity)
        }
      case Predicate.OptionValue(source) =>
        value(source, in, row) match {
          case Some(payload) => payload
          case _             => fail(Vector("option"), "Missing payload inside exists")
        }
    }

    private def test(node: Predicate.Test, in: Any, row: Any): Boolean = node match {
      case Predicate.Constant(result)                => result
      case Predicate.Compare(a, operator, b, scalar) =>
        val result = compare(scalar, value(a, in, row), value(b, in, row))
        operator match {
          case Predicate.Comparison.Equal              => result == 0
          case Predicate.Comparison.NotEqual           => result != 0
          case Predicate.Comparison.LessThan           => result < 0
          case Predicate.Comparison.LessThanOrEqual    => result <= 0
          case Predicate.Comparison.GreaterThan        => result > 0
          case Predicate.Comparison.GreaterThanOrEqual => result >= 0
        }
      case Predicate.And(a, b)    => test(a, in, row) && test(b, in, row)
      case Predicate.Or(a, b)     => test(a, in, row) || test(b, in, row)
      case Predicate.Not(a)       => !test(a, in, row)
      case Predicate.IsDefined(v) =>
        value(v, in, row) match {
          case Some(_) => true
          case None    => false
          case _       => fail(Vector("isDefined"), "Expected an Option field")
        }
      case Predicate.Exists(v, body) =>
        value(v, in, row) match {
          case Some(_) => test(body, in, row)
          case None    => false // !exists therefore evaluates to true for absence.
          case _       => fail(Vector("exists"), "Expected an Option field")
        }
    }

    private def compareKey(key: OrderBy.Key, left: Any, right: Any): Int = {
      def directed(a: Any, b: Any): Int = {
        val sign = java.lang.Integer.signum(compare(key.scalar, a, b))
        if (key.direction == OrderBy.Ascending) sign else -sign
      }
      key.missing match {
        case OrderBy.NotOptional => directed(left, right)
        case placement           =>
          (left, right) match {
            case (None, None)       => 0
            case (None, Some(_))    => if (placement == OrderBy.NullsFirst) -1 else 1
            case (Some(_), None)    => if (placement == OrderBy.NullsFirst) 1 else -1
            case (Some(a), Some(b)) => directed(a, b)
            case _ => fail(Vector("order"), "An optional order key did not contain Option")
          }
      }
    }

    private def compareKeys(left: Vector[Any], right: Vector[Any]): Int = {
      var index  = 0
      var result = 0
      while (index < ast.ordering.size && result == 0) {
        result = compareKey(ast.ordering(index), left(index), right(index))
        index += 1
      }
      result
    }

    /**
     * Apply to ONE input's candidate collection. Calling this per input keeps
     * top-one per parent, never accidentally top-one for the whole batch.
     * A database compiler can push these same stages down into storage instead.
     */
    def apply[In, Row](in: In, candidates: Vector[Row]): Either[FilterError, Vector[Row]] =
      protect {
        // 1. Filter while both original input and candidate output are in scope.
        val filtered = ast.predicate.fold(candidates)(p => candidates.filter(test(p, in, _)))

        // 2. Evaluate structured keys and sort lexicographically.
        // Preserve input order for ties in this interpreter only. Without a
        // total ordering, no cross-execution or cross-backend stability is promised.
        val ordered =
          if (ast.ordering.isEmpty) filtered
          else {
            val decorated = filtered.zipWithIndex.map { case (row, index) =>
              (row, ast.ordering.map(key => value(key.value, in, row)), index)
            }
            decorated.sortWith { (a, b) =>
              val result = compareKeys(a._2, b._2)
              if (result == 0) a._3 < b._3 else result < 0
            }.map(_._1)
          }

        // 3. Cardinality selection. Empty FirstRequired is NOT resolved here:
        // the effect interpreter calls its backend-supplied onEmpty hook.
        ast.cardinality match {
          case Selection.All                                   => ordered
          case Selection.FirstOption | Selection.FirstRequired => ordered.take(1)
        }
      }
  }
}
