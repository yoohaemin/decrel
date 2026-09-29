/*
 * Copyright (c) 2022 Haemin Yoo
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package decrel.reify

import scala.collection.mutable
import scala.ref.WeakReference

private[decrel] final class WeakKeyCache[V] {

  private case class Entry(
    key: WeakReference[AnyRef],
    value: V
  )

  private val entries = mutable.ArrayBuffer.empty[Entry]

  def getOrCreate(key: Any)(value: => V): V =
    entries.synchronized {
      val keyRef = key.asInstanceOf[AnyRef]
      val live   = mutable.ArrayBuffer.empty[Entry]
      var cached = Option.empty[V]

      live.sizeHint(entries.size)
      entries.foreach { entry =>
        entry.key.get match {
          case Some(existing) =>
            live += entry
            if (cached.isEmpty && existing == keyRef)
              cached = Some(entry.value)
          case None =>
            ()
        }
      }

      if (live.size != entries.size) {
        entries.clear()
        entries.addAll(live)
      }

      if (cached.nonEmpty)
        cached.get
      else {
        val created = value
        entries += Entry(WeakReference(keyRef), created)
        created
      }
    }
}
