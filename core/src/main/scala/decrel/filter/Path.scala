/*
 * Copyright (c) 2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter

/** A total, typed record-field path, produced by an optics adapter. */
final class Path[S, A] private[decrel] (val fields: Vector[String]) {
  def andThen[B](that: Path[A, B]): Path[S, B] = new Path(fields ++ that.fields)

  override def equals(other: Any): Boolean = other match {
    case that: Path[?, ?] => fields == that.fields
    case _                => false
  }

  override def hashCode(): Int  = fields.hashCode()
  override def toString: String = fields.mkString(".")
}
