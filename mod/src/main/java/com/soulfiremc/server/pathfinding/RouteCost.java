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

/// Structured route-cost diagnostics.
///
/// ARA* optimizes the scalar value returned by [#optimizationCost()]. Damage
/// risk participates in that value, while breaking and placement preferences
/// are already included in `durationCost` through policy penalties. Raw action
/// counts remain diagnostics and never masquerade as a multiplicative bound.
public record RouteCost(
  double expectedDamage,
  int irreversibleChanges,
  int placedBlocks,
  int brokenBlocks,
  double durationCost
) {
  public static final RouteCost ZERO = new RouteCost(0, 0, 0, 0, 0);

  public RouteCost {
    if (!Double.isFinite(expectedDamage) || expectedDamage < 0) {
      throw new IllegalArgumentException("Expected damage must be finite and non-negative");
    }
    if (irreversibleChanges < 0 || placedBlocks < 0 || brokenBlocks < 0) {
      throw new IllegalArgumentException("Route action counts must be non-negative");
    }
    if (!Double.isFinite(durationCost) || durationCost < 0) {
      throw new IllegalArgumentException("Route duration must be finite and non-negative");
    }
  }

  public RouteCost add(RouteCost other) {
    return new RouteCost(
      expectedDamage + other.expectedDamage,
      Math.addExact(irreversibleChanges, other.irreversibleChanges),
      Math.addExact(placedBlocks, other.placedBlocks),
      Math.addExact(brokenBlocks, other.brokenBlocks),
      durationCost + other.durationCost
    );
  }

  /// Returns the scalar non-negative cost optimized by ARA*.
  public double optimizationCost() {
    return expectedDamage + durationCost;
  }
}
