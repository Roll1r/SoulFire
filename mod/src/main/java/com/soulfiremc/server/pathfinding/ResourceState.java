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

/// The inventory values that can change available navigation transitions.
public record ResourceState(
  int usableBlockItems
) {
  public ResourceState {
    if (usableBlockItems < 0) {
      throw new IllegalArgumentException("Usable block item count must be non-negative");
    }
  }

  public static ResourceState withUsableBlockItems(int usableBlockItems) {
    return new ResourceState(usableBlockItems);
  }

  public boolean dominates(ResourceState other) {
    return usableBlockItems >= other.usableBlockItems;
  }

  public ResourceState addUsableBlockItems(int delta) {
    return new ResourceState(Math.addExact(usableBlockItems, delta));
  }
}
