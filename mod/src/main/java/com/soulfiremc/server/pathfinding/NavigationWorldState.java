/*
 * SoulFire
 * Copyright (C) 2026  AlexProgrammerDE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.soulfiremc.server.pathfinding;

import java.util.concurrent.atomic.AtomicLong;

/// Monotonic version for navigation-relevant client world data.
///
/// A route snapshot belongs to exactly one revision. A search session must
/// never repair its `OPEN`, `CLOSED`, or `INCONS` sets after this value changes.
public final class NavigationWorldState {
  private final AtomicLong revision = new AtomicLong();
  private Object worldIdentity;

  public synchronized long revision(Object currentWorld) {
    if (worldIdentity != currentWorld) {
      worldIdentity = currentWorld;
      return revision.incrementAndGet();
    }
    return revision.get();
  }

  public long markChanged() {
    return revision.incrementAndGet();
  }
}
