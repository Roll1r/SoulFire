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
  PRECISION(1.0, 1.0),
  NORMAL(2.5, 1.2),
  URGENT(3.0, 1.5),
  ESCAPE(4.0, 2.0);

  private final double initialEpsilon;
  private final double defaultQualityBound;

  RouteSearchMode(
    double initialEpsilon,
    double defaultQualityBound
  ) {
    this.initialEpsilon = initialEpsilon;
    this.defaultQualityBound = defaultQualityBound;
  }

  public double initialEpsilon() {
    return initialEpsilon;
  }

  public double defaultQualityBound() {
    return defaultQualityBound;
  }
}
