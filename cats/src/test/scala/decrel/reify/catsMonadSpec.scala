/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.*

object proofSpec {

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

  trait X[F[_]] extends catsMonad[F] {

    implicit def fooBarReify: Proof.Single[Foo.bar.type, Foo, Bar]

    implicit def fooBazReify: Proof.Single[Foo.baz.type, Foo, Baz]

    implicit def barBazReify: Proof.Single[Bar.baz.type, Bar, Baz]

  }

  def test[F[_]](x: X[F]): Unit = {
    import x._

    val test0 = Foo.bar.reify
//    val test1 = (Foo.bar >>: Bar.baz).reify
//    val test2 = (Foo.bar :>: Bar.baz).reify
//    val test3 = (Foo.bar & Foo.baz).reify
  }
}
