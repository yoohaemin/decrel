/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import decrel.Relation
import izumi.reflect.Tag
import zio.*
import zio.query.{ CompletedRequestMap, DataSource, ZQuery }

import scala.collection.{ mutable, BuildFrom, IterableOps }

trait zquerySyntax[R] {
  this: bifunctor.module[ZQuery[R, +*, +*]] =>

  extension [In](in: In) {

    def expand[Rel, E, Out](
      rel: Rel & Relation[In, Out]
    )(implicit
      proof: Proof[Rel & Relation[In, Out], In, E, Out]
    ): ZIO[R, E, Out] =
      proof.reify.apply(in).run

  }

}
