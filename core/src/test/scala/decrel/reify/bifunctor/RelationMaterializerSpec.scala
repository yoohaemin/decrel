/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify.bifunctor

import decrel.*
import zio.test.*
import decrel.reify.either.*

object proofSpec extends ZIOSpecDefault {

  // Foo -> Bar
  // Bar -> List[Foo]

  // Foo -> Baz
  // Baz -> Option[Foo]

  // Bar -> Baz
  // Baz -> Option[Bar]

  case class Foo()
  object Foo {
    case object self extends Relation.Self[Foo]
    case object bar  extends Relation.Single[Foo, Bar]
    case object baz  extends Relation.Single[Foo, Baz]
  }
  case class Bar()
  object Bar {
    case object self extends Relation.Self[Bar]
    case object foo  extends Relation.Many[Bar, List, Foo]
    case object baz  extends Relation.Single[Bar, Baz]
  }
  case class Baz()
  object Baz {
    case object self extends Relation.Self[Baz]
    case object foo  extends Relation.Optional[Baz, Foo]
    case object bar  extends Relation.Optional[Baz, Bar]
  }

  override def spec: Spec[Environment, Any] =
    suite("proof")(
      test("materializing for datasources without errors") {
        val composed = Foo.bar >>: Bar.baz

        implicit def fooBarReify: Proof.Single[Foo.bar.type, Foo, Nothing, Bar] =
          ???

        implicit def barBazReify: Proof.Single[Bar.baz.type, Bar, Nothing, Baz] =
          ???

        implicit def reified: ReifiedRelation[Foo, Nothing, Baz] =
          composed.reify

        assertCompletes
      },
      suite("materializing for datasources with errors")(
        test("on rhs") {
          val composed = Foo.bar >>: Bar.baz

          implicit def fooBarReify: Proof.Single[Foo.bar.type, Foo, Nothing, Bar] =
            ???

          implicit def barBazReify: Proof.Single[Bar.baz.type, Bar, Int, Baz] =
            ???

          implicit def Reified: ReifiedRelation[Foo, Int, Baz] =
            composed.reify

          assertCompletes
        },
        test("on lhs") {
          val composed = Foo.bar >>: Bar.baz

          implicit def fooBarReify: Proof.Single[Foo.bar.type, Foo, Int, Bar] =
            ???

          implicit def barBazReify: Proof.Single[Bar.baz.type, Bar, Nothing, Baz] =
            ???

          implicit def Reified: ReifiedRelation[Foo, Int, Baz] =
            composed.reify

          assertCompletes
        },
        test("on both sides") {
          val composed = Foo.bar >>: Bar.baz

          implicit def fooBarReify: Proof.Single[Foo.bar.type, Foo, Int, Bar] =
            ???

          implicit def barBazReify: Proof.Single[Bar.baz.type, Bar, Int, Baz] =
            ???

          implicit def Reified: ReifiedRelation[Foo, Int, Baz] =
            composed.reify

          assertCompletes
        },
        test("with supertype error on lhs") {
          val composed = Foo.bar >>: Bar.baz

          implicit def fooBarReify: Proof.Single[Foo.bar.type, Foo, AnyVal, Bar] =
            ???

          implicit def barBazReify: Proof.Single[Bar.baz.type, Bar, Int, Baz] =
            ???

          implicit def Reified: ReifiedRelation[Foo, AnyVal, Baz] =
            composed.reify

          assertCompletes
        },
        test("with supertype error on rhs") {
          val composed = Foo.bar >>: Bar.baz

          implicit def fooBarReify: Proof.Single[Foo.bar.type, Foo, Int, Bar] =
            ???

          implicit def barBazReify: Proof.Single[Bar.baz.type, Bar, AnyVal, Baz] =
            ???

          implicit def Reified: ReifiedRelation[Foo, AnyVal, Baz] =
            composed.reify

          assertCompletes
        }
      )
    )
}
