/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter

/** The closed set of literal and comparison types understood by the filter language. */
sealed abstract class Scalar[A](val name: String)

object Scalar {
  sealed abstract class Ordered[A](name: String) extends Scalar[A](name)

  implicit case object BooleanType    extends Scalar[Boolean]("Boolean")
  implicit case object ByteType       extends Ordered[Byte]("Byte")
  implicit case object ShortType      extends Ordered[Short]("Short")
  implicit case object IntType        extends Ordered[Int]("Int")
  implicit case object LongType       extends Ordered[Long]("Long")
  implicit case object FloatType      extends Ordered[Float]("Float")
  implicit case object DoubleType     extends Ordered[Double]("Double")
  implicit case object CharType       extends Ordered[Char]("Char")
  implicit case object StringType     extends Ordered[String]("String")
  implicit case object BigIntType     extends Ordered[BigInt]("BigInt")
  implicit case object BigDecimalType extends Ordered[BigDecimal]("BigDecimal")
}
