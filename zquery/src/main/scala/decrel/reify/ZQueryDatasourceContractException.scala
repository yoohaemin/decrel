/*
 * Copyright (c) 2022-2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

/** A batch callback did not return exactly one result for every requested key. */
final case class ZQueryDatasourceContractException(
  datasource: String,
  missingKeys: Set[Any],
  duplicateKeys: Set[Any],
  unexpectedKeys: Set[Any]
) extends IllegalArgumentException(
      s"Invalid results from $datasource: missing=$missingKeys, duplicate=$duplicateKeys, unexpected=$unexpectedKeys"
    )
