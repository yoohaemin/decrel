/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter.schema

import decrel.filter.{ FilterError, Path }
import zio.blocks.schema.{ DynamicOptic, Lens }

import scala.language.implicitConversions

/** Import to select ZIO Blocks record lenses in Decrel predicate expressions. */
object syntax {
  implicit def lensToPath[S, A](lens: Lens[S, A]): Path[S, A] =
    fromLens(lens).fold(error => throw new IllegalArgumentException(error.toString), identity)

  /** Only total record-field paths are part of the initial filter language. */
  def fromLens[S, A](lens: Lens[S, A]): Either[FilterError, Path[S, A]] = {
    val fields = Vector.newBuilder[String]
    val nodes  = lens.toDynamic.nodes.iterator
    var error  = Option.empty[FilterError]
    var index  = 0
    while (nodes.hasNext && error.isEmpty) {
      nodes.next() match {
        case DynamicOptic.Node.Field(name) => fields += name
        case other                         =>
          error = Some(
            FilterError(Vector("optic", index.toString), s"Unsupported optic node: $other")
          )
      }
      index += 1
    }
    error.toLeft(new Path[S, A](fields.result()))
  }
}
