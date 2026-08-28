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

/// An ordered route cost. Safety and irreversible changes take precedence.
/// Duration includes configured break and placement penalties. Raw action
/// counts only break ties, so conservation does not prevent useful edits.
public record RouteCost(
  double expectedDamage,
  int irreversibleChanges,
  int placedBlocks,
  int brokenBlocks,
  double durationCost
) implements Comparable<RouteCost> {
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

  public int compareEstimated(RouteCost other, double heuristic, double otherHeuristic) {
    var safetyComparison = compareSafety(other);
    if (safetyComparison != 0) {
      return safetyComparison;
    }
    var durationComparison = Double.compare(
      durationCost + heuristic,
      other.durationCost + otherHeuristic
    );
    if (durationComparison != 0) {
      return durationComparison;
    }
    return compareResourceUse(other);
  }

  public boolean noWorseThan(RouteCost other) {
    return expectedDamage <= other.expectedDamage
      && irreversibleChanges <= other.irreversibleChanges
      && durationCost <= other.durationCost
      && placedBlocks <= other.placedBlocks
      && brokenBlocks <= other.brokenBlocks;
  }

  @Override
  public int compareTo(RouteCost other) {
    var safetyComparison = compareSafety(other);
    if (safetyComparison != 0) {
      return safetyComparison;
    }
    var durationComparison = Double.compare(durationCost, other.durationCost);
    return durationComparison != 0
      ? durationComparison
      : compareResourceUse(other);
  }

  private int compareSafety(RouteCost other) {
    var damageComparison = Double.compare(expectedDamage, other.expectedDamage);
    if (damageComparison != 0) {
      return damageComparison;
    }
    var irreversibleComparison = Integer.compare(irreversibleChanges, other.irreversibleChanges);
    if (irreversibleComparison != 0) {
      return irreversibleComparison;
    }
    return 0;
  }

  private int compareResourceUse(RouteCost other) {
    var placementComparison = Integer.compare(placedBlocks, other.placedBlocks);
    if (placementComparison != 0) {
      return placementComparison;
    }
    return Integer.compare(brokenBlocks, other.brokenBlocks);
  }
}
