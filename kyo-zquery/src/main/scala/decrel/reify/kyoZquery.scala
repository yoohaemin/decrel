/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import _root_.kyo.{ Abort, Async, ZIOs }
import zio.*
import zio.query.{ CompletedRequestMap, DataSource, Request, ZQuery }
import scala.collection.{ mutable, SeqOps }

/** Optional ZQuery execution for the same Kyo proofs and callbacks. */
trait kyoZquery[E] extends kyoBatch[Async & Abort[E]] {
  private type Eff = Async & Abort[E]
  private case class Fetch(source: KyoBatchSource[Eff, Any, Any], key: Any) extends Request[E, Any]
  private val sources = mutable.Map.empty[KyoBatchSource[Eff, Any, Any], DataSource[Any, Fetch]]

  private def datasource(source: KyoBatchSource[Eff, Any, Any]): DataSource[Any, Fetch] =
    sources.synchronized {
      sources.getOrElseUpdate(
        source,
        new DataSource.Batched[Any, Fetch] {
          val identifier = "decrel-kyo-source:" + kyoZquery.nextId.incrementAndGet()
          def run(requests: Chunk[Fetch])(implicit
            trace: Trace
          ): ZIO[Any, Nothing, CompletedRequestMap] =
            ZIOs
              .run(source.batchExecute(requests.map(_.key).distinct.toVector))
              .map { values =>
                CompletedRequestMap.fromIterableWith(requests)(
                  identity,
                  request =>
                    values.get(request.key) match {
                      case Some(value) => Exit.succeed(value)
                      case None        =>
                        Exit.die(
                          new NoSuchElementException(
                            s"Response for request not found: ${request.key}"
                          )
                        )
                    }
                )
              }
              .catchAll(error =>
                ZIO.succeed(
                  CompletedRequestMap.fromIterableWith(requests)(identity, _ => Exit.fail(error))
                )
              )
        }
      )
    }

  private def translate[A](query: KyoBatchQuery[Eff, A]): ZQuery[Any, E, A] = ZQuery.suspend {
    query.step.result match {
      case KyoBatchQuery.Done(value)              => ZQuery.succeed(value)
      case pending: KyoBatchQuery.Pending[Eff, A] =>
        ZQuery
          .foreachBatched(pending.requests) { request =>
            ZQuery
              .fromRequest(Fetch(request.source, request.key))(datasource(request.source))
              .map(request.transform)
          }
          .flatMap { updates =>
            translate(
              pending.resume(updates.foldLeft(pending.initial)((state, update) => update(state)))
            )
          }
    }
  }

  implicit class KyoZqueryRelationOps[Rel, In, Out](private val rel: Rel & Relation[In, Out]) {
    def toZQuery(in: In)(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out]
    ): ZQuery[Any, E, Out] =
      translate(proof.reify.apply(in))

    def toZQueryMany[Coll[+A] <: Seq[A] & SeqOps[A, Coll, Coll[A]]](in: Coll[In])(implicit
      proof: Proof[Rel & Relation[In, Out], In, Out]
    ): ZQuery[Any, E, Coll[Out]] = translate(proof.reify.applyMultiple(in))
  }
}
object kyoZquery {
  private val nextId = new java.util.concurrent.atomic.AtomicLong()
}
