/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import decrel.selection.Selection
import zio.*
import zio.query.{ CompletedRequestMap, DataSource, ZQuery }
import scala.collection.IterableOps

/**
 * Additive interpreter for PR #379. Instantiate this instead of zquery[R].
 *
 * This is intentionally a separate leaf implementation interface, not another
 * optional argument threaded through both large existing Proof hierarchies.
 * The original proofs still handle ordinary/filtered leaves. These three new
 * proof instances turn selections back into ordinary composable relations.
 */
trait zquerySelection[R] extends zquery[R] {

  /**
   * A backend interprets the complete normalized plan for each original input.
   * Responses always use Vector[Row] as the wire format:
   *   All           -> every selected row, in the requested order
   *   FirstOption   -> zero or one row
   *   FirstRequired -> zero or one row; onEmpty defines the empty-case effect
   *
   * The callback can push all three stages into storage. It is not required to
   * fetch everything first. Unsupported compilation must fail, not be ignored.
   */
  final class SelectionSource[Rel, In, +E, Row] private[reify] (
    val relation: Rel,
    val atMostOne: Boolean,
    val execute: (Chunk[In], Selection.Plan[In, Row]) => ZIO[R, E, Chunk[(In, Vector[Row])]],
    val onEmpty: (In, Selection.Plan[In, Row]) => ZIO[R, E, Nothing]
  ) {
    private[reify] val identity: String = DatasourceId.fresh()
    private[reify] val identifiers      = new WeakKeyCache[String]
  }

  def implementManySelection[Rel, In, E, CC[+A], Row](
    relation: Rel & Relation.Many[In, CC, Row]
  )(
    execute: (Chunk[In], Selection.Plan[In, Row]) => ZIO[R, E, Chunk[(In, Vector[Row])]]
  )(
    onEmpty: (In, Selection.Plan[In, Row]) => ZIO[R, E, Nothing]
  ): SelectionSource[Rel, In, E, Row] =
    new SelectionSource[Rel, In, E, Row](relation, false, execute, onEmpty)

  def implementOptionalSelection[Rel, In, E, Row](
    relation: Rel & Relation.Optional[In, Row]
  )(
    execute: (Chunk[In], Selection.Plan[In, Row]) => ZIO[R, E, Chunk[(In, Vector[Row])]]
  )(
    onEmpty: (In, Selection.Plan[In, Row]) => ZIO[R, E, Nothing]
  ): SelectionSource[Rel, In, E, Row] =
    new SelectionSource[Rel, In, E, Row](relation, true, execute, onEmpty)

  /** For Single.filter(...).head: filtering has made the intermediate optional. */
  def implementSingleSelection[Rel, In, E, Row](
    relation: Rel & Relation.Single[In, Row]
  )(
    execute: (Chunk[In], Selection.Plan[In, Row]) => ZIO[R, E, Chunk[(In, Vector[Row])]]
  )(
    onEmpty: (In, Selection.Plan[In, Row]) => ZIO[R, E, Nothing]
  ): SelectionSource[Rel, In, E, Row] =
    new SelectionSource[Rel, In, E, Row](relation, true, execute, onEmpty)

  private final case class SelectionRequest[In, E, Row](
    sourceId: String,
    relation: Any,
    plan: Selection.Plan[In, Row],
    input: In
  ) extends zio.query.Request[E, Vector[Row]]

  private def materialize[Rel, In, E, Row, Out](
    source: SelectionSource[Rel, In, E, Row],
    base: Rel,
    plan: Selection.Plan[In, Row]
  )(
    finish: (In, Vector[Row]) => ZIO[R, E, Out]
  ): ReifiedRelation[In, E, Out] = {
    require(
      base == source.relation,
      "Selection proof was registered for a different relation value"
    )
    plan.toAst.fold(e => throw new IllegalArgumentException(e.toString), _ => ())

    // Per-source identifiers prevent two interpreter instances from borrowing
    // each other's implementation. Equal *whole plans* share a data source.
    val ds = new DataSource.Batched[R, SelectionRequest[In, E, Row]] {
      override val identifier: String =
        source.identifiers.getOrCreate(plan)(DatasourceId.fresh())

      override def run(requests: Chunk[SelectionRequest[In, E, Row]])(implicit
        trace: Trace
      ): ZIO[R, Nothing, CompletedRequestMap] = {
        // Compare complete inputs: same customer ID with a different budget is
        // still a different input, exactly as required by PR #379's filters.
        val inputs = requests.map(_.input).distinct

        source
          .execute(inputs, plan)
          .map { pairs =>
            val expected = inputs.toSet
            val returned = pairs.map(_._1)
            require(returned.distinct.size == returned.size, "Duplicate selection response input")
            require(returned.toSet == expected, "Selection backend omitted or added an input")
            if (source.atMostOne || plan.cardinality != Selection.All)
              require(pairs.forall(_._2.size <= 1), "Selection backend did not honor cardinality")
            pairs.toMap
          }
          .exit
          .map { result =>
            // Failures/defects complete all requests; none are left hanging.
            CompletedRequestMap.fromIterableWith[E, SelectionRequest[In, E, Row], Vector[Row]](
              requests
            )(
              (r: zio.query.Request[E, Vector[Row]]) => r,
              (r: SelectionRequest[In, E, Row]) => result.mapExit(rows => rows(r.input))
            )
          }
      }
    }

    new ReifiedRelation.Custom[In, E, Out] {
      override def apply(in: In): ZQuery[R, E, Out] =
        ZQuery
          .fromRequest[R, E, SelectionRequest[In, E, Row], Vector[Row]](
            SelectionRequest(source.identity, base, plan, in)
          )(ds)
          .flatMap(rows => ZQuery.fromZIO(finish(in, rows)))

      override def applyMultiple[Coll[+A] <: Iterable[A] & IterableOps[A, Coll, Coll[A]]](
        inputs: Coll[In]
      ): ZQuery[R, E, Coll[Out]] =
        ZQuery.foreachPar(inputs)(in => apply(in))
    }
  }

  implicit def orderedSelectionProof[Rel, In, E, Row](implicit
    source: SelectionSource[Rel, In, E, Row]
  ): Proof.Many[Selection.OrderedMany[Rel, In, Row], In, E, Vector, Row, Nothing] =
    new Proof.Many[Selection.OrderedMany[Rel, In, Row], In, E, Vector, Row, Nothing] {
      override private[decrel] def reifyRelation(
        relation: Selection.OrderedMany[Rel, In, Row] & Relation[In, Vector[Row]]
      ): ReifiedRelation[In, E, Vector[Row]] =
        materialize(source, relation.base, relation.plan)((_, rows) => ZIO.succeed(rows))
    }

  implicit def optionalSelectionProof[Rel, In, E, Row](implicit
    source: SelectionSource[Rel, In, E, Row]
  ): Proof.Optional[Selection.Optional[Rel, In, Row], In, E, Row, Nothing] =
    new Proof.Optional[Selection.Optional[Rel, In, Row], In, E, Row, Nothing] {
      override private[decrel] def reifyRelation(
        relation: Selection.Optional[Rel, In, Row] & Relation[In, Option[Row]]
      ): ReifiedRelation[In, E, Option[Row]] =
        materialize(source, relation.base, relation.plan)((_, rows) => ZIO.succeed(rows.headOption))
    }

  implicit def requiredSelectionProof[Rel, In, E, Row](implicit
    source: SelectionSource[Rel, In, E, Row]
  ): Proof.Single[Selection.Required[Rel, In, Row], In, E, Row, Nothing] =
    new Proof.Single[Selection.Required[Rel, In, Row], In, E, Row, Nothing] {
      override private[decrel] def reifyRelation(
        relation: Selection.Required[Rel, In, Row] & Relation[In, Row]
      ): ReifiedRelation[In, E, Row] =
        materialize(source, relation.base, relation.plan) { (in, rows) =>
          rows.headOption match {
            case Some(row) => ZIO.succeed(row)
            // No generic throw/default row. The implementation owns failure.
            case None => source.onEmpty(in, relation.plan)
          }
        }
    }
}
