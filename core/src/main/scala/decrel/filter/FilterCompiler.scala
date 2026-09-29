/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter

/** A construction or compilation failure at a path in the predicate tree. */
final case class FilterError(path: Vector[String], reason: String) {
  override def toString: String = s"${("$" +: path).mkString(".")}: $reason"
}

/**
 * Backend-owned interpretation boundary. Implementations must preserve two-valued
 * Boolean/Option semantics or return an error, never silently drop a condition.
 */
abstract class FilterCompiler[Plan] {
  final def compile[In, Row](predicate: Predicate[In, Row]): Either[FilterError, Plan] =
    predicate.toAst.flatMap(compileAst)

  protected def compileAst(predicate: Predicate.Test): Either[FilterError, Plan]
}
