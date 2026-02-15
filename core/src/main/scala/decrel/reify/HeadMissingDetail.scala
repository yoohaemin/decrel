/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

sealed trait HeadMissingSourceKind

object HeadMissingSourceKind {
  case object OptionalNone extends HeadMissingSourceKind
  case object ManyEmpty    extends HeadMissingSourceKind
}

final case class HeadMissingDetail[In](
  relationType: String,
  input: In,
  sourceKind: HeadMissingSourceKind
)
