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

/// The explicit route-quality and latency policy for a search.
public enum RouteSearchMode {
  PRECISION(1.0),
  NORMAL(1.2),
  URGENT(1.5),
  ESCAPE(1.5);

  private final double heuristicWeight;

  RouteSearchMode(double heuristicWeight) {
    this.heuristicWeight = heuristicWeight;
  }

  public double heuristicWeight() {
    return heuristicWeight;
  }
}
