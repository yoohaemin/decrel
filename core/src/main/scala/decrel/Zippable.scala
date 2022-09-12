/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/*
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 *   Copyright 2017-2022 John A. De Goes and the ZIO Contributors
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

// This file is based on https://github.com/zio/zio/blob/v2.0.2/core/shared/src/main/scala/zio/Zippable.scala
// And subsequently modified to add ZippableLowPriority2, adding support for prepending to tuples.

package decrel

trait Zippable[-A, -B] {
  type Out
  def zip(left: A, right: B): Out
}

object Zippable extends ZippableLowPriority1 {

  type Out[-A, -B, C] = Zippable[A, B] { type Out = C }

  implicit def ZippableLeftIdentity[A]: Zippable.Out[Unit, A, A] =
    new Zippable[Unit, A] {
      type Out = A
      def zip(left: Unit, right: A) =
        right
    }
}

trait ZippableLowPriority1 extends ZippableLowPriority2 {

  implicit def ZippableRightIdentity[A]: Zippable.Out[A, Unit, A] =
    new Zippable[A, Unit] {
      type Out = A
      def zip(left: A, right: Unit) =
        left
    }
}

trait ZippableLowPriority2 extends ZippableLowPriority3 {

  implicit def Zippable3Prepend[Z, A, B]: Zippable.Out[Z, (A, B), (Z, A, B)] =
    new Zippable[Z, (A, B)] {
      type Out = (Z, A, B)
      def zip(left: Z, right: (A, B)): (Z, A, B) =
        (left, right._1, right._2)
    }

  implicit def Zippable4Prepend[Z, A, B, C]: Zippable.Out[Z, (A, B, C), (Z, A, B, C)] =
    new Zippable[Z, (A, B, C)] {
      type Out = (Z, A, B, C)
      def zip(left: Z, right: (A, B, C)): (Z, A, B, C) =
        (left, right._1, right._2, right._3)
    }

  implicit def Zippable5Prepend[Z, A, B, C, D]: Zippable.Out[Z, (A, B, C, D), (Z, A, B, C, D)] =
    new Zippable[Z, (A, B, C, D)] {
      type Out = (Z, A, B, C, D)
      def zip(left: Z, right: (A, B, C, D)): (Z, A, B, C, D) =
        (left, right._1, right._2, right._3, right._4)
    }

  implicit def Zippable6Prepend[Z, A, B, C, D, E]
    : Zippable.Out[Z, (A, B, C, D, E), (Z, A, B, C, D, E)] =
    new Zippable[Z, (A, B, C, D, E)] {
      type Out = (Z, A, B, C, D, E)
      def zip(left: Z, right: (A, B, C, D, E)): (Z, A, B, C, D, E) =
        (left, right._1, right._2, right._3, right._4, right._5)
    }

  implicit def Zippable7Prepend[Z, A, B, C, D, E, F]
    : Zippable.Out[Z, (A, B, C, D, E, F), (Z, A, B, C, D, E, F)] =
    new Zippable[Z, (A, B, C, D, E, F)] {
      type Out = (Z, A, B, C, D, E, F)
      def zip(left: Z, right: (A, B, C, D, E, F)): (Z, A, B, C, D, E, F) =
        (left, right._1, right._2, right._3, right._4, right._5, right._6)
    }

  implicit def Zippable8Prepend[Z, A, B, C, D, E, F, G]
    : Zippable.Out[Z, (A, B, C, D, E, F, G), (Z, A, B, C, D, E, F, G)] =
    new Zippable[Z, (A, B, C, D, E, F, G)] {
      type Out = (Z, A, B, C, D, E, F, G)
      def zip(left: Z, right: (A, B, C, D, E, F, G)): (Z, A, B, C, D, E, F, G) =
        (left, right._1, right._2, right._3, right._4, right._5, right._6, right._7)
    }

  implicit def Zippable9Prepend[Z, A, B, C, D, E, F, G, H]
    : Zippable.Out[Z, (A, B, C, D, E, F, G, H), (Z, A, B, C, D, E, F, G, H)] =
    new Zippable[Z, (A, B, C, D, E, F, G, H)] {
      type Out = (Z, A, B, C, D, E, F, G, H)
      def zip(left: Z, right: (A, B, C, D, E, F, G, H)): (Z, A, B, C, D, E, F, G, H) =
        (left, right._1, right._2, right._3, right._4, right._5, right._6, right._7, right._8)
    }

  implicit def Zippable10Prepend[Z, A, B, C, D, E, F, G, H, I]
    : Zippable.Out[Z, (A, B, C, D, E, F, G, H, I), (Z, A, B, C, D, E, F, G, H, I)] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I)
      def zip(left: Z, right: (A, B, C, D, E, F, G, H, I)): (Z, A, B, C, D, E, F, G, H, I) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9
        )
    }

  implicit def Zippable11Prepend[Z, A, B, C, D, E, F, G, H, I, J]
    : Zippable.Out[Z, (A, B, C, D, E, F, G, H, I, J), (Z, A, B, C, D, E, F, G, H, I, J)] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J)
      def zip(left: Z, right: (A, B, C, D, E, F, G, H, I, J)): (Z, A, B, C, D, E, F, G, H, I, J) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10
        )
    }

  implicit def Zippable12Prepend[Z, A, B, C, D, E, F, G, H, I, J, K]
    : Zippable.Out[Z, (A, B, C, D, E, F, G, H, I, J, K), (Z, A, B, C, D, E, F, G, H, I, J, K)] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11
        )
    }

  implicit def Zippable13Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L]: Zippable.Out[
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L),
    (Z, A, B, C, D, E, F, G, H, I, J, K, L)
  ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12
        )
    }

  implicit def Zippable14Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M]: Zippable.Out[
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M),
    (Z, A, B, C, D, E, F, G, H, I, J, K, L, M)
  ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13
        )
    }

  implicit def Zippable15Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N]: Zippable.Out[
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
    (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N)
  ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14
        )
    }

  implicit def Zippable16Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O]: Zippable.Out[
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
    (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
  ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14,
          right._15
        )
    }

  implicit def Zippable17Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P]: Zippable.Out[
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
    (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
  ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14,
          right._15,
          right._16
        )
    }

  implicit def Zippable18Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q]
    : Zippable.Out[
      Z,
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
      (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
    ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14,
          right._15,
          right._16,
          right._17
        )
    }

  implicit def Zippable19Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R]
    : Zippable.Out[
      Z,
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
      (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
    ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14,
          right._15,
          right._16,
          right._17,
          right._18
        )
    }

  implicit def Zippable20Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S]
    : Zippable.Out[
      Z,
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
      (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
    ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14,
          right._15,
          right._16,
          right._17,
          right._18,
          right._19
        )
    }

  implicit def Zippable21Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T]
    : Zippable.Out[
      Z,
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
      (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
    ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14,
          right._15,
          right._16,
          right._17,
          right._18,
          right._19,
          right._20
        )
    }

  implicit def Zippable22Prepend[Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U]
    : Zippable.Out[
      Z,
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
      (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
    ] =
    new Zippable[Z, (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)] {
      type Out = (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
      def zip(
        left: Z,
        right: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U)
      ): (Z, A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U) =
        (
          left,
          right._1,
          right._2,
          right._3,
          right._4,
          right._5,
          right._6,
          right._7,
          right._8,
          right._9,
          right._10,
          right._11,
          right._12,
          right._13,
          right._14,
          right._15,
          right._16,
          right._17,
          right._18,
          right._19,
          right._20,
          right._21
        )
    }
}

trait ZippableLowPriority3 extends ZippableLowPriority4 {

  implicit def Zippable3[A, B, Z]: Zippable.Out[(A, B), Z, (A, B, Z)] =
    new Zippable[(A, B), Z] {
      type Out = (A, B, Z)
      def zip(left: (A, B), right: Z): (A, B, Z) =
        (left._1, left._2, right)
    }

  implicit def Zippable4[A, B, C, Z]: Zippable.Out[(A, B, C), Z, (A, B, C, Z)] =
    new Zippable[(A, B, C), Z] {
      type Out = (A, B, C, Z)
      def zip(left: (A, B, C), right: Z): (A, B, C, Z) =
        (left._1, left._2, left._3, right)
    }

  implicit def Zippable5[A, B, C, D, Z]: Zippable.Out[(A, B, C, D), Z, (A, B, C, D, Z)] =
    new Zippable[(A, B, C, D), Z] {
      type Out = (A, B, C, D, Z)
      def zip(left: (A, B, C, D), right: Z): (A, B, C, D, Z) =
        (left._1, left._2, left._3, left._4, right)
    }

  implicit def Zippable6[A, B, C, D, E, Z]: Zippable.Out[(A, B, C, D, E), Z, (A, B, C, D, E, Z)] =
    new Zippable[(A, B, C, D, E), Z] {
      type Out = (A, B, C, D, E, Z)
      def zip(left: (A, B, C, D, E), right: Z): (A, B, C, D, E, Z) =
        (left._1, left._2, left._3, left._4, left._5, right)
    }

  implicit def Zippable7[A, B, C, D, E, F, Z]
    : Zippable.Out[(A, B, C, D, E, F), Z, (A, B, C, D, E, F, Z)] =
    new Zippable[(A, B, C, D, E, F), Z] {
      type Out = (A, B, C, D, E, F, Z)
      def zip(left: (A, B, C, D, E, F), right: Z): (A, B, C, D, E, F, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, right)
    }

  implicit def Zippable8[A, B, C, D, E, F, G, Z]
    : Zippable.Out[(A, B, C, D, E, F, G), Z, (A, B, C, D, E, F, G, Z)] =
    new Zippable[(A, B, C, D, E, F, G), Z] {
      type Out = (A, B, C, D, E, F, G, Z)
      def zip(left: (A, B, C, D, E, F, G), right: Z): (A, B, C, D, E, F, G, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, right)
    }

  implicit def Zippable9[A, B, C, D, E, F, G, H, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H), Z, (A, B, C, D, E, F, G, H, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H), Z] {
      type Out = (A, B, C, D, E, F, G, H, Z)
      def zip(left: (A, B, C, D, E, F, G, H), right: Z): (A, B, C, D, E, F, G, H, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, right)
    }

  implicit def Zippable10[A, B, C, D, E, F, G, H, I, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I), Z, (A, B, C, D, E, F, G, H, I, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, Z)
      def zip(left: (A, B, C, D, E, F, G, H, I), right: Z): (A, B, C, D, E, F, G, H, I, Z) =
        (left._1, left._2, left._3, left._4, left._5, left._6, left._7, left._8, left._9, right)
    }

  implicit def Zippable11[A, B, C, D, E, F, G, H, I, J, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J), Z, (A, B, C, D, E, F, G, H, I, J, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, Z)
      def zip(left: (A, B, C, D, E, F, G, H, I, J), right: Z): (A, B, C, D, E, F, G, H, I, J, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          right
        )
    }

  implicit def Zippable12[A, B, C, D, E, F, G, H, I, J, K, Z]
    : Zippable.Out[(A, B, C, D, E, F, G, H, I, J, K), Z, (A, B, C, D, E, F, G, H, I, J, K, Z)] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          right
        )
    }

  implicit def Zippable13[A, B, C, D, E, F, G, H, I, J, K, L, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          right
        )
    }

  implicit def Zippable14[A, B, C, D, E, F, G, H, I, J, K, L, M, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          right
        )
    }

  implicit def Zippable15[A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          right
        )
    }

  implicit def Zippable16[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          right
        )
    }

  implicit def Zippable17[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          right
        )
    }

  implicit def Zippable18[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          right
        )
    }

  implicit def Zippable19[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          right
        )
    }

  implicit def Zippable20[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z]: Zippable.Out[
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
    Z,
    (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z)
  ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          left._19,
          right
        )
    }

  implicit def Zippable21[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z]
    : Zippable.Out[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
      Z,
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z)
    ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          left._19,
          left._20,
          right
        )
    }

  implicit def Zippable22[A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z]
    : Zippable.Out[
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
      Z,
      (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z)
    ] =
    new Zippable[(A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U), Z] {
      type Out = (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z)
      def zip(
        left: (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U),
        right: Z
      ): (A, B, C, D, E, F, G, H, I, J, K, L, M, N, O, P, Q, R, S, T, U, Z) =
        (
          left._1,
          left._2,
          left._3,
          left._4,
          left._5,
          left._6,
          left._7,
          left._8,
          left._9,
          left._10,
          left._11,
          left._12,
          left._13,
          left._14,
          left._15,
          left._16,
          left._17,
          left._18,
          left._19,
          left._20,
          left._21,
          right
        )
    }
}

trait ZippableLowPriority4 {

  implicit def Zippable2[A, B]: Zippable.Out[A, B, (A, B)] =
    new Zippable[A, B] {
      type Out = (A, B)
      def zip(left: A, right: B): Out = (left, right)
    }
}
