/* SPDX-License-Identifier: MPL-2.0 */
package decrel.selection

import decrel.filter.FilterError

/**
 * Like the PR's FilterCompiler, but sees the complete edge-local policy.
 *
 * Backends must preserve FILTER -> ORDER -> CARDINALITY and per-input grouping.
 * An unsupported field, scalar, ordering or semantic rule must return Left.
 * Silently ignoring a stage is not an implementation strategy.
 *
 * FirstRequired and FirstOption both select zero or one candidate. The former's
 * empty case is resolved by the interpreter's onEmpty effect, not by this
 * compiler manufacturing a value or deciding an application error type.
 */
abstract class SelectionCompiler[BackendPlan] {
  final def compile[In, Row](plan: Selection.Plan[In, Row]): Either[FilterError, BackendPlan] =
    plan.toAst.flatMap(compileAst)

  protected def compileAst(ast: Selection.Ast): Either[FilterError, BackendPlan]
}
