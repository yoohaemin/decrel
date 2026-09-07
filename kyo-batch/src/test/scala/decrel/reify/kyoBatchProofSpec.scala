/*
 * Copyright (c) 2025 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*
import zio.test.*

object kyoBatchProofSpec extends ZIOSpecDefault {

  case class Book(id: Book.Id)
  object Book {
    case class Id(value: String)

    case object fetch extends Relation.Single[Id, Book]
  }

  sealed trait BookFilter
  case class ExactBookFilter(id: Book.Id) extends BookFilter

  private val book1 = Book(Book.Id("book1"))
  private val book2 = Book(Book.Id("book2"))
  private val books = Map(book1.id -> book1, book2.id -> book2)

  object testKyo extends kyoBatch[Any]

  override def spec: Spec[TestEnvironment, Any] =
    suite("Kyo batch proof")(
      test("Source cache reuses the source for the same relation key") {
        val cache = new testKyo.SourceCache[Book.Id, Book]

        val source1 = cache.getOrCreate(Book.fetch)(_ => books)
        val source2 = cache.getOrCreate(Book.fetch)(_ => Map.empty)

        assertTrue(source1.asInstanceOf[AnyRef] eq source2.asInstanceOf[AnyRef])
      },
      test("Source cache reuses the source for an equal filtered relation key") {
        val cache = new testKyo.SourceCache[Book.Id, Option[Book]]

        val filter   = ExactBookFilter(book1.id)
        val filtered = Book.fetch.filter(filter)

        val source1 = cache.getOrCreate(filtered)(_ => Map(book1.id -> Some(book1)))
        val source2 = cache.getOrCreate(Book.fetch.filter(filter))(_ => Map.empty)

        assertTrue(source1.asInstanceOf[AnyRef] eq source2.asInstanceOf[AnyRef])
      },
      test("Source cache keeps different filtered relation keys separate") {
        val cache = new testKyo.SourceCache[Book.Id, Option[Book]]

        val source1 = cache.getOrCreate(Book.fetch.filter(ExactBookFilter(book1.id)))(_ =>
          Map(book1.id -> Some(book1))
        )
        val source2 = cache.getOrCreate(Book.fetch.filter(ExactBookFilter(book2.id)))(_ =>
          Map(book2.id -> Some(book2))
        )

        assertTrue(!(source1.asInstanceOf[AnyRef] eq source2.asInstanceOf[AnyRef]))
      },
      test("Proofs keep different filtered relations isolated at runtime") {
        import testKyo.*

        var seenCalls = Vector.empty[(Option[BookFilter], Seq[Book.Id])]

        implicit val filteredBookFetch: Proof.Single[
          Book.fetch.type & Relation.Single[Book.Id, Book],
          Book.Id,
          Book,
          BookFilter
        ] = implementSingleDatasource[Book.fetch.type, Book.Id, Book, BookFilter](Book.fetch) {
          (ins, filter) =>
            seenCalls = seenCalls :+ (filter -> ins)
            filter match {
              case Some(ExactBookFilter(id)) =>
                ins.collect {
                  case current if current == id => current -> Some(books(current))
                }.toMap
              case _ =>
                Map.empty
            }
        }

        val result = _root_.kyo.Kyo.zip(
          Book.fetch.filter(ExactBookFilter(book1.id)).startingFrom(book1.id),
          Book.fetch.filter(ExactBookFilter(book2.id)).startingFrom(book2.id)
        )

        assertTrue(
          result == (Some(book1), Some(book2)),
          seenCalls.toSet == Set(
            (Some(ExactBookFilter(book1.id)), Seq(book1.id)),
            (Some(ExactBookFilter(book2.id)), Seq(book2.id))
          )
        )
      }
    )
}
