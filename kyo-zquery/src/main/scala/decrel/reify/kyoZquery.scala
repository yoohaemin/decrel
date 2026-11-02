/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import izumi.reflect.Tag
import _root_.kyo.{ <, Abort, Async, ZIOs }
import zio.Chunk
import scala.collection.SeqOps

/**
 * ZQuery-backed relations with batch callbacks implemented using Kyo effects.
 * Handle additional Kyo effects inside the callback before returning it.
 */
trait kyoZquery extends zquery[Any] {
  def implementSingleKyoDatasource[Rel: Tag, In, E, Out](
    relation: Rel & Relation.Single[In, Out]
  )(
    batchExecute: Chunk[In] => Chunk[(In, Out)] < (Abort[E] & Async)
  )(implicit tag: Tag[Out]): Proof.Single[Rel & Relation.Single[In, Out], In, E, Out] =
    implementSingleDatasource[Rel, In, E, Out](relation)(ids => ZIOs.run(batchExecute(ids)))

  def implementOptionalKyoDatasource[Rel: Tag, In, E, Out](
    relation: Rel & Relation.Optional[In, Out]
  )(
    batchExecute: Chunk[In] => Chunk[(In, Option[Out])] < (Abort[E] & Async)
  )(implicit tag: Tag[Out]): Proof.Optional[Rel & Relation.Optional[In, Out], In, E, Out] =
    implementOptionalDatasource[Rel, In, E, Out](relation)(ids => ZIOs.run(batchExecute(ids)))

  def implementManyKyoDatasource[Rel: Tag, In, E, Out, CC[+A] <: Seq[A] & SeqOps[A, CC, CC[A]]](
    relation: Rel & Relation.Many[In, CC, Out]
  )(
    batchExecute: Chunk[In] => Chunk[(In, CC[Out])] < (Abort[E] & Async)
  )(implicit tag: Tag[CC[Out]]): Proof.Many[Rel & Relation.Many[In, CC, Out], In, E, CC, Out] =
    implementManyDatasource[Rel, In, E, CC, Out](relation)(ids => ZIOs.run(batchExecute(ids)))

  def implementCustomKyoDatasource[Tree, In, E, Out](
    relation: Relation.Custom[Tree, In, Out]
  )(
    batchExecute: Chunk[In] => Chunk[(In, Out)] < (Abort[E] & Async)
  )(implicit
    tag: Tag[Relation.Custom[Tree, In, Out]]
  ): Proof[Relation.Custom[Tree, In, Out], In, E, Out] =
    implementCustomDatasource(relation)(ids => ZIOs.run(batchExecute(ids)))
}
