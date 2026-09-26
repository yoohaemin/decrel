/*
 * Copyright (c) 2026 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

/** Identifiers must stay distinct when queries from different module instances are combined. */
private[decrel] object DatasourceId {
  private var nextId = 0L

  def fresh(): String = synchronized {
    nextId += 1
    s"RelationDatasource:$nextId"
  }
}
