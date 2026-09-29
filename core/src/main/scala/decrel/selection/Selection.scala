/* SPDX-License-Identifier: MPL-2.0 */
package decrel.selection

import decrel.Relation
import decrel.filter.{ Expr, FilterError, OrderBy, Predicate }
import scala.annotation.implicitNotFound
import scala.collection.IterableOps

object Selection {
  sealed trait Cardinality
  case object All           extends Cardinality
  case object FirstOption   extends Cardinality
  case object FirstRequired extends Cardinality

  /**
   * Normal form, not a bag of freely reordered operations.
   * Evaluation is ALWAYS filter -> order -> cardinality, separately for each In.
   * All, FirstOption and FirstRequired remain different request/cache keys.
   */
  final case class Plan[In, Row](
    predicate: Option[Predicate[In, Row]] = None,
    ordering: Option[OrderBy[In, Row]] = None,
    cardinality: Cardinality = All
  ) {
    def toAst: Either[FilterError, Ast] = {
      val condition: Either[FilterError, Option[Predicate.Test]] = predicate match {
        case Some(p) => p.toAst.map(Some(_))
        case None    => Right(None)
      }
      val keys: Either[FilterError, Vector[OrderBy.Key]] = ordering match {
        case Some(o) => o.toAst
        case None    => Right(Vector.empty)
      }
      for { p <- condition; o <- keys } yield Ast(p, o, cardinality)
    }
  }

  sealed trait Stage
  final case class FilterStage(predicate: Option[Predicate.Test]) extends Stage
  final case class OrderStage(keys: Vector[OrderBy.Key])          extends Stage
  final case class CardinalityStage(cardinality: Cardinality)     extends Stage

  final case class Ast(
    predicate: Option[Predicate.Test],
    ordering: Vector[OrderBy.Key],
    cardinality: Cardinality
  ) {
    // Even an omitted filter/order has its fixed position in the pipeline.
    def stages: Vector[Stage] = Vector(
      FilterStage(predicate),
      OrderStage(ordering),
      CardinalityStage(cardinality)
    )
  }

  @implicitNotFound(
    "Filtering/ordering cannot follow orderBy. Build filter -> orderBy -> head/headOption."
  )
  sealed trait NoEarlierStage

  @implicitNotFound(
    "Cardinality is the final selection stage. Compose another relation with >>: instead."
  )
  sealed trait NoStageAfterCardinality

  private def unreachable: Nothing =
    throw new IllegalStateException("Uninhabited staging evidence was supplied")

  /** Real members prevent fallback to the PR's implicit .filter syntax. */
  sealed trait AfterOrder[In, Row] {
    final def filter(p: Predicate[In, Row])(implicit no: NoEarlierStage): Nothing = unreachable
    final def filter(f: (Expr[In, Row, In], Expr[In, Row, Row]) => Predicate[In, Row])(implicit
      no: NoEarlierStage
    ): Nothing                                                                   = unreachable
    final def orderBy(o: OrderBy[In, Row])(implicit no: NoEarlierStage): Nothing = unreachable
    final def orderBy(f: (Expr[In, Row, In], Expr[In, Row, Row]) => OrderBy[In, Row])(implicit
      no: NoEarlierStage
    ): Nothing = unreachable
  }

  sealed trait Terminal[In, Row] {
    final def filter(p: Predicate[In, Row])(implicit no: NoStageAfterCardinality): Nothing =
      unreachable
    final def filter(f: (Expr[In, Row, In], Expr[In, Row, Row]) => Predicate[In, Row])(implicit
      no: NoStageAfterCardinality
    ): Nothing = unreachable
    final def orderBy(o: OrderBy[In, Row])(implicit no: NoStageAfterCardinality): Nothing =
      unreachable
    final def orderBy(f: (Expr[In, Row, In], Expr[In, Row, Row]) => OrderBy[In, Row])(implicit
      no: NoStageAfterCardinality
    ): Nothing                                                          = unreachable
    final def head(implicit no: NoStageAfterCardinality): Nothing       = unreachable
    final def headOption(implicit no: NoStageAfterCardinality): Nothing = unreachable
  }

  /**
   * These extend the PR's open leaf traits, so no change to sealed Relation is
   * necessary. The ZQuery adapter supplies their ordinary Proof.Single/Optional/
   * Many instances; the existing composition derivations then do the rest.
   *
   * The original base relation (and its singleton type) is never replaced with
   * the intermediate Filtered/Ordered wrapper in the backend lookup.
   */
  final case class OrderedMany[Rel, In, Row] private[selection] (
    base: Rel,
    plan: Plan[In, Row]
  ) extends Relation.Many[In, Vector, Row]
      with AfterOrder[In, Row] {
    require(plan.ordering.nonEmpty && plan.cardinality == All)

    def head: Required[Rel, In, Row] =
      Required(base, plan.copy(cardinality = FirstRequired))
    def headOption: Optional[Rel, In, Row] =
      Optional(base, plan.copy(cardinality = FirstOption))
  }

  final case class Required[Rel, In, Row] private[selection] (
    base: Rel,
    plan: Plan[In, Row]
  ) extends Relation.Single[In, Row]
      with Terminal[In, Row] {
    require(plan.cardinality == FirstRequired)
  }

  final case class Optional[Rel, In, Row] private[selection] (
    base: Rel,
    plan: Plan[In, Row]
  ) extends Relation.Optional[In, Row]
      with Terminal[In, Row] {
    require(plan.cardinality == FirstOption)
  }
}

/** Import in addition to decrel._ and decrel.filter.OrderBy._. */
object syntax {
  import Selection.*

  implicit final class ManySelectionOps[Rel, In, CC[+A], Row](
    private val relation: Rel & Relation.Many[In, CC, Row]
  ) {
    def orderBy(order: OrderBy[In, Row]): OrderedMany[Rel, In, Row] =
      OrderedMany[Rel, In, Row](relation, Plan[In, Row](ordering = Some(order.checked)))

    def orderBy(
      build: (Expr[In, Row, In], Expr[In, Row, Row]) => OrderBy[In, Row]
    ): OrderedMany[Rel, In, Row] = orderBy(OrderBy.build(build))

    /** No order guarantee: choose a member, not a reproducible member. */
    def head: Required[Rel, In, Row] =
      Required[Rel, In, Row](relation, Plan[In, Row](cardinality = FirstRequired))
    def headOption: Optional[Rel, In, Row] =
      Optional[Rel, In, Row](relation, Plan[In, Row](cardinality = FirstOption))
  }

  implicit final class OptionalSelectionOps[Rel, In, Row](
    private val relation: Rel & Relation.Optional[In, Row]
  ) {
    def head: Required[Rel, In, Row] =
      Required[Rel, In, Row](relation, Plan[In, Row](cardinality = FirstRequired))
  }

  /** The Iterable bound keeps this disjoint from Option's filtered syntax. */
  implicit final class FilteredManySelectionOps[
    Rel,
    In,
    CC[+A] <: Iterable[A] & IterableOps[A, CC, CC[A]],
    Row
  ](
    private val relation: Relation.Filtered[Rel, CC[Row], In, CC[Row], Predicate[In, Row]]
  ) {
    def orderBy(order: OrderBy[In, Row]): OrderedMany[Rel, In, Row] =
      OrderedMany[Rel, In, Row](
        relation.relation,
        Plan(Some(relation.filter), Some(order.checked), All)
      )

    def orderBy(
      build: (Expr[In, Row, In], Expr[In, Row, Row]) => OrderBy[In, Row]
    ): OrderedMany[Rel, In, Row] = orderBy(OrderBy.build(build))

    def head: Required[Rel, In, Row] =
      Required[Rel, In, Row](relation.relation, Plan(Some(relation.filter), None, FirstRequired))
    def headOption: Optional[Rel, In, Row] =
      Optional[Rel, In, Row](relation.relation, Plan(Some(relation.filter), None, FirstOption))
  }

  /** Also handles Single.filter(...), whose result is optional in PR #379. */
  implicit final class FilteredOptionalSelectionOps[Rel, BaseOut, In, Row](
    private val relation: Relation.Filtered[Rel, BaseOut, In, Option[Row], Predicate[In, Row]]
  ) {
    def head: Required[Rel, In, Row] =
      Required[Rel, In, Row](relation.relation, Plan(Some(relation.filter), None, FirstRequired))
  }
}
