/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import _root_.kyo.*

/** A single callback shared by decrel and native Kyo callers. */
final class KyoBatchSource[S, I, A] private (val batchExecute: Seq[I] => Map[I, A] < S) {
  private val grouped = Batch.sourceMap[Vector[I], Vector[A], S] { groups =>
    batchExecute(groups.flatten.distinct).map { results =>
      groups.map(group => group -> group.map(results)).toMap
    }
  }
  def toBatch(in: I): A < (Batch & S) = grouped(Vector(in)).map(_.head)
  private[reify] def toBatchMany(in: Vector[I]): Vector[A] < (Batch & S) =
    if (in.isEmpty) Vector.empty[A] else grouped(in)
}
object KyoBatchSource {
  def apply[S, I, A](f: Seq[I] => Map[I, A] < S): KyoBatchSource[S, I, A] = new KyoBatchSource(f)
}

// Fetch keeps its erased source/key pair together; Lift never participates in key deduplication.
private[reify] sealed trait KyoBatchRequest[S, A] {
  def map[B](f: A => B): KyoBatchRequest[S, B]
}
private[reify] object KyoBatchRequest {
  final case class Fetch[S, A](source: KyoBatchSource[S, Any, Any], key: Any, transform: Any => A)
      extends KyoBatchRequest[S, A] {
    def map[B](f: A => B): KyoBatchRequest[S, B] = Fetch(source, key, transform.andThen(f))
  }
  final case class Lift[S, A](effect: () => A < S) extends KyoBatchRequest[S, A] {
    def map[B](f: A => B): KyoBatchRequest[S, B] = Lift(() => effect().map(f))
  }
}
