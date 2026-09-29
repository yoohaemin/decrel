/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.filter.schema

import decrel.*
import decrel.filter.{ Predicate, Scalar }
import decrel.filter.schema.syntax.*
import zio.blocks.schema.{ CompanionOptics, Lens, Schema }
import zio.test.*

object SchemaFilterSpec extends ZIOSpecDefault {
  case class Address(city: String)
  object Address extends CompanionOptics[Address] {
    implicit val schema: Schema[Address] = Schema.derived
    val city: Lens[Address, String]      = optic(_.city)
  }

  case class Customer(budget: BigDecimal, address: Address)
  object Customer extends CompanionOptics[Customer] {
    implicit val schema: Schema[Customer]  = Schema.derived
    val budget: Lens[Customer, BigDecimal] = optic(_.budget)
    val address: Lens[Customer, Address]   = optic(_.address)
    val city: Lens[Customer, String]       = optic(_.address.city)
    case object books extends Relation.Many[Customer, List, Book]
  }

  case class Book(price: BigDecimal, discount: Option[Int], publisher: Option[Address])
  object Book extends CompanionOptics[Book] {
    implicit val schema: Schema[Book]          = Schema.derived
    val price: Lens[Book, BigDecimal]          = optic(_.price)
    val discount: Lens[Book, Option[Int]]      = optic(_.discount)
    val publisher: Lens[Book, Option[Address]] = optic(_.publisher)
  }

  override def spec: Spec[TestEnvironment, Any] = suite("ZIO Blocks filter optics")(
    test("a many edge filters a typed candidate using input and output lenses") {
      val relation = Customer.books.filter { (in, out) =>
        (out(Book.price) <= in(Customer.budget)) && out(Book.discount).exists(_ > 0)
      }
      import Predicate.*
      val discount = Field(Output, Vector("discount"))
      assertTrue(
        relation.filter.toAst == Right(
          And(
            Compare(
              Field(Output, Vector("price")),
              Comparison.LessThanOrEqual,
              Field(Input, Vector("budget")),
              Scalar.BigDecimalType
            ),
            Exists(
              discount,
              Compare(
                OptionValue(discount),
                Comparison.GreaterThan,
                Literal(0, Scalar.IntType),
                Scalar.IntType
              )
            )
          )
        )
      )
    },
    test("nested lenses and successive field selection have the same path") {
      val first  = Predicate.build[Customer, Book]((in, _) => in(Customer.city) === "Seoul")
      val second =
        Predicate.build[Customer, Book]((in, _) => in(Customer.address)(Address.city) === "Seoul")
      assertTrue(first == second, first.hashCode == second.hashCode)
    },
    test("optional records allow total field selection inside exists") {
      val predicate = Predicate.build[Customer, Book] { (in, out) =>
        out(Book.publisher).exists(publisher => publisher(Address.city) === in(Customer.city))
      }
      import Predicate.*
      val publisher = Field(Output, Vector("publisher"))
      assertTrue(
        predicate.toAst == Right(
          Exists(
            publisher,
            Compare(
              Field(OptionValue(publisher), Vector("city")),
              Comparison.Equal,
              Field(Input, Vector("address", "city")),
              Scalar.StringType
            )
          )
        )
      )
    },
    test("a lens from the wrong root does not typecheck") {
      typeCheck("""import decrel.filter._
        import decrel.filter.schema.syntax._
        import decrel.filter.schema.SchemaFilterSpec._
        Predicate.build[Customer, Book]((in, out) => out(Customer.budget) > BigDecimal(0))
      """).map(result => assertTrue(result.isLeft))
    },
    test("a traversal cannot be used as a scalar field") {
      typeCheck("""import decrel.filter._
        import decrel.filter.schema.syntax._
        import zio.blocks.schema._
        case class Bag(values: List[Int])
        object Bag extends CompanionOptics[Bag] {
          implicit val schema: Schema[Bag] = Schema.derived
          val values: Lens[Bag, List[Int]] = optic(_.values)
        }
        Predicate.build[Unit, Bag]((_, out) => out(Bag.values.listValues[Int]) > 0)
      """).map(result => assertTrue(result.isLeft))
    },
    test("optional fields must be inspected explicitly") {
      typeCheck("""import decrel.filter._
        import decrel.filter.schema.syntax._
        import decrel.filter.schema.SchemaFilterSpec._
        Predicate.build[Customer, Book]((_, out) => out(Book.discount) === Some(1))
      """).map(result => assertTrue(result.isLeft))
    }
  )
}
