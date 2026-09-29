/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

import scala.compiletime.testing.{ typeCheckErrors, typeChecks }
import zio.test.*

object FilterSyntaxSpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] =
    suite("filter syntax")(
      test("single filtering still typechecks") {
        assertTrue(
          typeChecks(
            """import decrel.*
import decrel.syntax.*

object Book {
  case object fetch extends Relation.Single[Int, Int]
}

val relation = Book.fetch.filter(decrel.filter.Predicate.always[Int, Int])
"""
          )
        )
      },
      test("single, optional, and many relations are edges; custom is declared") {
        assertTrue(
          typeChecks(
            """import decrel.*
import decrel.syntax.*

object Book {
  case object fetch extends Relation.Single[Int, Int]
  case object maybe extends Relation.Optional[Int, Int]
}
object User {
  case object books extends Relation.Many[Int, List, Int]
}

val singleEdge: Relation.Edge[Int, Int] = Book.fetch
val optionalEdge: Relation.Edge[Int, Option[Int]] = Book.maybe
val manyEdge: Relation.Edge[Int, List[Int]] = User.books
val customDeclared: Relation.Declared[Int, Int] = Book.fetch.customImpl
"""
          )
        )
      },
      test("filtered single, optional, and many relations cannot be filtered again") {
        val filteredSingleErrors = typeCheckErrors(
          """import decrel.*
import decrel.syntax.*

object Book {
  case object fetch extends Relation.Single[Int, Int]
}

val relation = Book.fetch.filter(decrel.filter.Predicate.always[Int, Int]).filter(decrel.filter.Predicate.never[Int, Int])
"""
        )

        val filteredOptionalErrors = typeCheckErrors(
          """import decrel.*
import decrel.syntax.*

object Book {
  case object currentRental extends Relation.Optional[Int, Int]
}

val relation = Book.currentRental.filter(decrel.filter.Predicate.always[Int, Int]).filter(decrel.filter.Predicate.never[Int, Int])
"""
        )

        val filteredManyErrors = typeCheckErrors(
          """import decrel.*
import decrel.syntax.*

object User {
  case object rentals extends Relation.Many[Int, List, Int]
}

val relation = User.rentals.filter(decrel.filter.Predicate.always[Int, Int]).filter(decrel.filter.Predicate.never[Int, Int])
"""
        )

        assertTrue(
          filteredSingleErrors.nonEmpty,
          filteredOptionalErrors.nonEmpty,
          filteredManyErrors.nonEmpty
        )
      },
      test("filtered relation wrappers are not directly constructible outside decrel") {
        val errors = typeCheckErrors(
          """package outside

import decrel.*

object Book {
  case object fetch extends Relation.Single[Int, Int]
}

val relation = Relation.Filtered(Book.fetch, 1)
"""
        )

        assertTrue(errors.nonEmpty)
      },
      test("legacy per-cardinality filtered names are not public") {
        val singleErrors = typeCheckErrors(
          """package outside

import decrel.*

object Book {
  case object fetch extends Relation.Single[Int, Int]
}

val relation = Relation.Filtered.Single(Book.fetch, 1)
"""
        )
        val optionalErrors = typeCheckErrors(
          """package outside

import decrel.*

object Book {
  case object fetch extends Relation.Optional[Int, Int]
}

val relation = Relation.Filtered.Optional(Book.fetch, 1)
"""
        )
        val manyErrors = typeCheckErrors(
          """package outside

import decrel.*

object Book {
  case object fetch extends Relation.Many[Int, List, Int]
}

val relation = Relation.Filtered.Many(Book.fetch, 1)
"""
        )

        assertTrue(
          singleErrors.nonEmpty,
          optionalErrors.nonEmpty,
          manyErrors.nonEmpty
        )
      },
      test("custom wrappers require declared relations") {
        val customConstructorErrors = typeCheckErrors(
          """package outside

import decrel.*
import decrel.syntax.*

object R1 {
  case object rel extends Relation.Single[Int, Int]
}
object R2 {
  case object rel extends Relation.Single[Int, Int]
}

val composed = R1.rel >>: R2.rel
val custom = Relation.Custom(composed)
"""
        )
        val customSyntaxErrors = typeCheckErrors(
          """package outside

import decrel.*
import decrel.syntax.*

object R1 {
  case object rel extends Relation.Single[Int, Int]
}
object R2 {
  case object rel extends Relation.Single[Int, Int]
}

val composed = R1.rel >>: R2.rel
val custom = composed.customImpl
"""
        )

        assertTrue(
          customConstructorErrors.nonEmpty,
          customSyntaxErrors.nonEmpty
        )
      },
      test("filtered relations expose declared base relations") {
        assertTrue(
          typeChecks(
            """import decrel.*
import decrel.syntax.*

object Book {
  case object fetch extends Relation.Single[Int, Int]
  case object maybe extends Relation.Optional[Int, Int]
  case object many extends Relation.Many[Int, List, Int]
}

val filteredSingle = Book.fetch.filter(decrel.filter.Predicate.always[Int, Int])
val singleDeclared: Relation.Declared[Int, Int] = filteredSingle.relation

val filteredOptional = Book.maybe.filter(decrel.filter.Predicate.always[Int, Int])
val optionalDeclared: Relation.Declared[Int, Option[Int]] = filteredOptional.relation

val filteredMany = Book.many.filter(decrel.filter.Predicate.always[Int, Int])
val manyDeclared: Relation.Declared[Int, List[Int]] = filteredMany.relation

"""
          )
        )
      },
      test("filtered relations are not edges") {
        val errors = typeCheckErrors(
          """import decrel.*
import decrel.syntax.*

object Book {
  case object fetch extends Relation.Single[Int, Int]
}

val edge: Relation.Edge[Int, Option[Int]] = Book.fetch.filter(decrel.filter.Predicate.always[Int, Int])
"""
        )

        assertTrue(errors.nonEmpty)
      }
    )
}
