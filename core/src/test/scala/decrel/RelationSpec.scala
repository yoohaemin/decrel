/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel

import decrel.Relation.Composed
import zio.test.*

object RelationSpec extends ZIOSpecDefault {

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
    suite("relation")(
      suite("composition")(
        test("one to one") {
          val composed: Relation.Composed.Single[
            Foo.bar.type & Relation.Single[Foo, Bar],
            Foo, // Input
            Bar,
            Bar.baz.type & Relation.Single[Bar, Baz],
            Bar,
            Baz // Output
          ] =
            Foo.bar >>: Bar.baz

          val _: Relation[Foo, Baz] = composed

          assertCompletes
        },
        test("one to one, zipped") {
          val composed: Composed.Zipped[
            Foo.bar.type & Relation.Single[Foo, Bar],
            Foo, // Input
            Bar,
            Composed.Single[
              Foo.bar.type & Relation.Single[Foo, Bar],
              Foo,
              Bar,
              Bar.baz.type & Relation.Single[Bar, Baz],
              Bar,
              Baz
            ],
            Foo,
            Baz,
            (Bar, Baz) // Output
          ] =
            Foo.bar :>: Bar.baz

          val _: Relation[Foo, (Bar, Baz)] = composed

          assertCompletes
        },
        test("one to optional") {
          val composed: Composed.Optional[
            Baz.foo.type & Relation.Optional[Baz, Foo],
            Baz,
            Foo,
            Foo.bar.type & Relation[Foo, Bar],
            Foo,
            Bar
          ] = Baz.foo >>: Foo.bar

          val _: Relation[Baz, Option[Bar]] = composed

          assertCompletes
        },
        test("one to optional, zipped") {
          val composed: Composed.Optional[
            Baz.foo.type & Relation.Optional[Baz, Foo],
            Baz,
            Foo,
            Composed.Zipped[
              Relation.Self[Foo],
              Foo,
              Foo,
              Foo.bar.type & Relation[Foo, Bar],
              Foo,
              Bar,
              (Foo, Bar)
            ],
            Foo,
            (Foo, Bar)
          ] =
            Baz.foo :>: Foo.bar

          val _: Relation[Baz, Option[(Foo, Bar)]] = composed

          assertCompletes
        },
        test("one to many") {
          val composed: Composed.Many[
            Bar.foo.type & Relation.Many[Bar, List, Foo],
            Bar,
            Foo,
            Foo.baz.type & Relation[Foo, Baz],
            Foo,
            Baz,
            List
          ] = Bar.foo >>: Foo.baz

          val _: Relation[Bar, List[Baz]] = composed

          assertCompletes
        },
        test("one to many, zipped") {
          val composed: Composed.Many[
            Bar.foo.type & Relation.Many[Bar, List, Foo],
            Bar,
            Foo,
            Composed.Zipped[
              Relation.Self[Foo],
              Foo,
              Foo,
              Foo.baz.type & Relation[Foo, Baz],
              Foo,
              Baz,
              (Foo, Baz)
            ],
            Foo,
            (Foo, Baz),
            List
          ] =
            Bar.foo :>: Foo.baz

          val _: Relation[Bar, List[(Foo, Baz)]] = composed

          assertCompletes
        },
        test("zipping one") {
          val composed: Composed.Zipped[
            Foo.self.type & Relation[Foo, Foo],
            Foo,
            Foo,
            Foo.bar.type & Relation[Foo, Bar],
            Foo,
            Bar,
            (Foo, Bar)
          ] =
            Foo.self & Foo.bar

          val _: Relation[Foo, (Foo, Bar)] = composed

          assertCompletes
        },
        test("zipping more than one") {
          val composed: Composed.Zipped[
            Composed.Zipped[
              Foo.self.type & Relation[Foo, Foo],
              Foo,
              Foo,
              Foo.bar.type & Relation[Foo, Bar],
              Foo,
              Bar,
              (Foo, Bar)
            ] & Relation[Foo, (Foo, Bar)],
            Foo,
            (Foo, Bar),
            Foo.baz.type & Relation[Foo, Baz],
            Foo,
            Baz,
            (Foo, Bar, Baz)
          ] =
            Foo.self & Foo.bar & Foo.baz

          val _: Relation[Foo, (Foo, Bar, Baz)] = composed

          assertCompletes
        },
        // Compose one
        test("compose one & a zipped relation") {
          val composed: Composed.Single[
            Foo.baz.type & Relation.Single[Foo, Baz],
            Foo,
            Baz,
            Composed.Zipped[
              Baz.foo.type & Relation[Baz, Option[Foo]],
              Baz,
              Option[Foo],
              Baz.bar.type & Relation[Baz, Option[Bar]],
              Baz,
              Option[Bar],
              (Option[Foo], Option[Bar])
            ] & Relation[Baz, (Option[Foo], Option[Bar])],
            Baz,
            (Option[Foo], Option[Bar])
          ] =
            Foo.baz >>: (Baz.foo & Baz.bar)

          val _: Relation[Foo, (Option[Foo], Option[Bar])] = composed

          assertCompletes
        },
        test("compose one zipped & a zipped relation") {
          val composed: Composed.Zipped[
            Foo.baz.type & Relation.Single[Foo, Baz],
            Foo,
            Baz,
            Composed.Single[
              Foo.baz.type & Relation.Single[Foo, Baz],
              Foo,
              Baz,
              Composed.Zipped[
                Baz.foo.type & Relation[Baz, Option[Foo]],
                Baz,
                Option[Foo],
                Baz.bar.type & Relation[Baz, Option[Bar]],
                Baz,
                Option[Bar],
                (Option[Foo], Option[Bar])
              ] & Relation[Baz, (Option[Foo], Option[Bar])],
              Baz,
              (Option[Foo], Option[Bar])
            ],
            Foo,
            (Option[Foo], Option[Bar]),
            (Baz, Option[Foo], Option[Bar])
          ] =
            Foo.baz :>: (Baz.foo & Baz.bar)

          val _: Relation[Foo, (Baz, Option[Foo], Option[Bar])] = composed

          assertCompletes
        },
        test("compose one compose one zipped & a zipped relation") {
          val composed: Composed.Zipped[
            Foo.self.type & Relation[Foo, Foo],
            Foo,
            Foo,
            Composed.Zipped[
              Foo.baz.type & Relation.Single[Foo, Baz],
              Foo,
              Baz,
              Composed.Single[
                Foo.baz.type & Relation.Single[Foo, Baz],
                Foo,
                Baz,
                Composed.Zipped[
                  Baz.foo.type & Relation[Baz, Option[Foo]],
                  Baz,
                  Option[Foo],
                  Baz.bar.type & Relation[Baz, Option[Bar]],
                  Baz,
                  Option[Bar],
                  (Option[Foo], Option[Bar])
                ] & Relation[Baz, (Option[Foo], Option[Bar])],
                Baz,
                (Option[Foo], Option[Bar])
              ],
              Foo,
              (Option[Foo], Option[Bar]),
              (Baz, Option[Foo], Option[Bar])
            ] & Relation[Foo, (Baz, Option[Foo], Option[Bar])],
            Foo,
            (Baz, Option[Foo], Option[Bar]),
            (Foo, Baz, Option[Foo], Option[Bar])
          ] =
            Foo.self & (Foo.baz :>: (Baz.foo & Baz.bar))

          val _: Relation[Foo, (Foo, Baz, Option[Foo], Option[Bar])] = composed

          assertCompletes
        },
        // Compose optional
        test("compose optional & a zipped relation") {
          val composed: Composed.Optional[
            Baz.foo.type & Relation.Optional[Baz, Foo],
            Baz,
            Foo,
            Composed.Zipped[
              Foo.bar.type & Relation[Foo, Bar],
              Foo,
              Bar,
              Foo.baz.type & Relation[Foo, Baz],
              Foo,
              Baz,
              (Bar, Baz)
            ] & Relation[Foo, (Bar, Baz)],
            Foo,
            (Bar, Baz)
          ] =
            Baz.foo >>: (Foo.bar & Foo.baz)

          val _: Relation[Baz, Option[(Bar, Baz)]] = composed

          assertCompletes
        },
        test("compose optional zipped & a zipped relation") {
          val composed: Composed.Optional[
            Baz.foo.type & Relation.Optional[Baz, Foo],
            Baz,
            Foo,
            Composed.Zipped[
              Relation.Self[Foo],
              Foo,
              Foo,
              Composed.Zipped[
                Foo.bar.type & Relation[Foo, Bar],
                Foo,
                Bar,
                Foo.baz.type & Relation[Foo, Baz],
                Foo,
                Baz,
                (Bar, Baz)
              ] & Relation[Foo, (Bar, Baz)],
              Foo,
              (Bar, Baz),
              (Foo, Bar, Baz)
            ],
            Foo,
            (Foo, Bar, Baz)
          ] =
            Baz.foo :>: (Foo.bar & Foo.baz)

          val _: Relation[Baz, Option[(Foo, Bar, Baz)]] = composed

          assertCompletes
        },
        test("compose optional compose optional zipped & a zipped relation") {
          val composed: Composed.Zipped[
            Baz.self.type & Relation[Baz, Baz],
            Baz,
            Baz,
            Composed.Optional[
              Baz.foo.type & Relation.Optional[Baz, Foo],
              Baz,
              Foo,
              Composed.Zipped[
                Relation.Self[Foo],
                Foo,
                Foo,
                Composed.Zipped[
                  Foo.bar.type & Relation[Foo, Bar],
                  Foo,
                  Bar,
                  Foo.baz.type & Relation[Foo, Baz],
                  Foo,
                  Baz,
                  (Bar, Baz)
                ] & Relation[Foo, (Bar, Baz)],
                Foo,
                (Bar, Baz),
                (Foo, Bar, Baz)
              ],
              Foo,
              (Foo, Bar, Baz)
            ] & Relation[Baz, Option[(Foo, Bar, Baz)]],
            Baz,
            Option[(Foo, Bar, Baz)],
            (Baz, Option[(Foo, Bar, Baz)])
          ] =
            Baz.self & (Baz.foo :>: (Foo.bar & Foo.baz))

          val _: Relation[Baz, (Baz, Option[(Foo, Bar, Baz)])] = composed

          assertCompletes
        },
        // Compose many
        test("compose many & a zipped relation") {
          val composed: Composed.Many[
            Bar.foo.type & Relation.Many[Bar, List, Foo],
            Bar,
            Foo,
            Composed.Zipped[
              Foo.bar.type & Relation[Foo, Bar],
              Foo,
              Bar,
              Foo.baz.type & Relation[Foo, Baz],
              Foo,
              Baz,
              (Bar, Baz)
            ] & Relation[Foo, (Bar, Baz)],
            Foo,
            (Bar, Baz),
            List
          ] =
            Bar.foo >>: (Foo.bar & Foo.baz)

          val _: Relation[Bar, List[(Bar, Baz)]] = composed

          assertCompletes
        },
        test("compose many zipped & a zipped relation") {
          val composed: Composed.Many[
            Bar.foo.type & Relation.Many[Bar, List, Foo],
            Bar,
            Foo,
            Composed.Zipped[
              Relation.Self[Foo],
              Foo,
              Foo,
              Composed.Zipped[
                Foo.bar.type & Relation[Foo, Bar],
                Foo,
                Bar,
                Foo.baz.type & Relation[Foo, Baz],
                Foo,
                Baz,
                (Bar, Baz)
              ] & Relation[Foo, (Bar, Baz)],
              Foo,
              (Bar, Baz),
              (Foo, Bar, Baz)
            ],
            Foo,
            (Foo, Bar, Baz),
            List
          ] =
            Bar.foo :>: (Foo.bar & Foo.baz)

          val _: Relation[Bar, List[(Foo, Bar, Baz)]] = composed

          assertCompletes
        },
        test("compose many compose many zipped & a zipped relation") {
          val composed: Composed.Zipped[
            Bar.self.type & Relation[Bar, Bar],
            Bar,
            Bar,
            Composed.Many[
              Bar.foo.type & Relation.Many[Bar, List, Foo],
              Bar,
              Foo,
              Composed.Zipped[
                Relation.Self[Foo],
                Foo,
                Foo,
                Composed.Zipped[
                  Foo.bar.type & Relation[Foo, Bar],
                  Foo,
                  Bar,
                  Foo.baz.type & Relation[Foo, Baz],
                  Foo,
                  Baz,
                  (Bar, Baz)
                ] & Relation[Foo, (Bar, Baz)],
                Foo,
                (Bar, Baz),
                (Foo, Bar, Baz)
              ],
              Foo,
              (Foo, Bar, Baz),
              List
            ] & Relation[Bar, List[(Foo, Bar, Baz)]],
            Bar,
            List[(Foo, Bar, Baz)],
            (Bar, List[(Foo, Bar, Baz)])
          ] =
            Bar.self & (Bar.foo :>: (Foo.bar & Foo.baz))

          val _: Relation[Bar, (Bar, List[(Foo, Bar, Baz)])] = composed

          assertCompletes
        }
      )
    )
}
