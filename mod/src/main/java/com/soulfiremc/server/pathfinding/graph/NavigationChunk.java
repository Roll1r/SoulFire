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
package com.soulfiremc.server.pathfinding.graph;

import com.soulfiremc.server.pathfinding.SFVec3i;

/// Identifies one chunk whose blocks a navigation snapshot could not read.
public record NavigationChunk(int x, int z)
  implements Comparable<NavigationChunk> {
  public static NavigationChunk containing(SFVec3i position) {
    return new NavigationChunk(position.x >> 4, position.z >> 4);
  }

  @Override
  public int compareTo(NavigationChunk other) {
    var xComparison = Integer.compare(x, other.x);
    return xComparison != 0 ? xComparison : Integer.compare(z, other.z);
  }
}
