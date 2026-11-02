/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import _root_.kyo.*
import scala.util.control.TailCalls.{ done, tailcall, TailRec }

/** A reusable relation computation. Independent requests are submitted to Kyo Batch together. */
sealed abstract class KyoBatchQuery[S, A] {
  import KyoBatchQuery.*

  // Trampoline both descent through binds and continuation resumption after a batch.
  private[reify] def step: TailRec[Step[S, A]]

  final def map[B](f: A => B): KyoBatchQuery[S, B] = flatMap(a => pure(f(a)))

  final def flatMap[B](f: A => KyoBatchQuery[S, B]): KyoBatchQuery[S, B] =
    new KyoBatchQuery[S, B] {
      def step: TailRec[Step[S, B]] = tailcall(KyoBatchQuery.this.step).flatMap {
        case Done(value)            => tailcall(f(value).step)
        case pending: Pending[S, A] =>
          done(new Pending[S, B] {
            type State = pending.State
            val initial                                   = pending.initial
            val requests                                  = pending.requests
            def resume(state: State): KyoBatchQuery[S, B] = defer(pending.resume(state)).flatMap(f)
          })
      }
    }

  /** Leave batching to the caller while preserving one result for this plan. */
  final def toBatch: A < (Batch & S) = step.result match {
    case Done(value)            => value
    case pending: Pending[S, A] =>
      val groups = pending.requests.groupBy(_.source).toVector
      Kyo
        .foreach(groups) { case (source, requests) =>
          source.toBatchMany(requests.map(_.key)).map { values =>
            requests.zip(values).map { case (request, value) => request.transform(value) }
          }
        }
        .map { updates =>
          pending
            .resume(updates.flatten.foldLeft(pending.initial)((state, update) => update(state)))
            .toBatch
        }
  }

  private[reify] final def execute: A < S = Batch.run(toBatch).map(_.head)
  // Compatibility for low-level callers; relation startingFrom is the public shortcut.
  private[reify] final def run: A < S = execute

}

object KyoBatchQuery {
  private[reify] sealed trait Step[S, A]
  private[reify] final case class Done[S, A](value: A) extends Step[S, A]
  private[reify] abstract class Pending[S, A]          extends Step[S, A] {
    type State
    val initial: State
    val requests: Vector[KyoBatchRequest[S, State => State]]
    def resume(state: State): KyoBatchQuery[S, A]
  }

  private def defer[S, A](query: => KyoBatchQuery[S, A]): KyoBatchQuery[S, A] =
    new KyoBatchQuery[S, A] {
      def step: TailRec[Step[S, A]] = tailcall(query.step)
    }

  def pure[S, A](value: A): KyoBatchQuery[S, A] = new KyoBatchQuery[S, A] {
    def step: TailRec[Step[S, A]] = done(Done(value))
  }

  /** Lift a custom Kyo effect without running it while constructing a relation graph. */
  def lift[S, A](effect: => A < S): KyoBatchQuery[S, A] =
    request(KyoBatchSource[S, Unit, A](_ => effect.map(a => Map(() -> a))), ())

  private[reify] def request[S, I, A](
    source: KyoBatchSource[S, I, A],
    key: I
  ): KyoBatchQuery[S, A] =
    new KyoBatchQuery[S, A] { query =>
      def step: TailRec[Step[S, A]] = done(new Pending[S, A] {
        type State = KyoBatchQuery[S, A]
        val initial: State = query
        val requests       = Vector(
          KyoBatchRequest[S, State => State](
            source.asInstanceOf[KyoBatchSource[S, Any, Any]],
            key,
            value => (_: State) => pure[S, A](value.asInstanceOf[A])
          )
        )
        def resume(state: State): KyoBatchQuery[S, A] = state
      })
    }

  private def pending[S, A](step: Step[S, A]): Pending[S, A] = step match {
    case value: Pending[S, A] => value
    case Done(value)          =>
      new Pending[S, A] {
        type State = Unit
        val initial  = ()
        val requests = Vector.empty[KyoBatchRequest[S, State => State]]
        def resume(state: State): KyoBatchQuery[S, A] = pure(value)
      }
  }

  private[reify] def zipWith[S, A, B, C](left: KyoBatchQuery[S, A], right: KyoBatchQuery[S, B])(
    f: (A, B) => C
  ): KyoBatchQuery[S, C] = new KyoBatchQuery[S, C] {
    def step: TailRec[Step[S, C]] = tailcall(left.step).flatMap { leftStep =>
      tailcall(right.step).map { rightStep =>
        (leftStep, rightStep) match {
          case (Done(a), Done(b)) => Done(f(a, b))
          case (a, b)             =>
            val l = pending(a)
            val r = pending(b)
            new Pending[S, C] {
              type State = (l.State, r.State)
              val initial: State = (l.initial, r.initial)
              // Each update changes only its own subtree. Updates commute even if Batch reorders sources.
              val requests: Vector[KyoBatchRequest[S, State => State]] =
                l.requests.map(_.map(update => (state: State) => (update(state._1), state._2))) ++
                  r.requests.map(_.map(update => (state: State) => (state._1, update(state._2))))
              def resume(state: State): KyoBatchQuery[S, C] =
                zipWith(defer(l.resume(state._1)), defer(r.resume(state._2)))(f)
            }
        }
      }
    }
  }

  private[reify] def traverse[S, A, B](
    values: Vector[A]
  )(f: A => KyoBatchQuery[S, B]): KyoBatchQuery[S, Vector[B]] = {
    def loop(from: Int, until: Int): KyoBatchQuery[S, Vector[B]] =
      if (from == until) pure(Vector.empty)
      else if (until - from == 1) f(values(from)).map(Vector(_))
      else {
        val middle = from + (until - from) / 2
        zipWith(loop(from, middle), loop(middle, until))(_ ++ _)
      }
    loop(0, values.size)
  }
}
