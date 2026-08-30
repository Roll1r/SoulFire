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
package com.soulfiremc.server.pathfinding.graph.constraint;

import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.graph.GraphInstructions;
import net.minecraft.world.level.BlockGetter;

import java.util.List;
import java.util.Objects;

/// Adds a live, request-scoped threat field to an ordinary path constraint.
///
/// Exposure is a non-negative transition cost, so an ARA* quality certificate
/// remains valid. The exclusion radius prevents a safe bot from entering a
/// threat's immediate reach. A bot already inside one exclusion may only move
/// away from it. When exclusions overlap around the bot, that per-threat rule
/// can forbid every transition, so the soft exposure costs arbitrate egress
/// while newly entered exclusions remain forbidden.
public final class ThreatAvoidanceConstraint
  implements DelegatePathConstraint {
  private static final double DISTANCE_TOLERANCE = 0.25;

  private final PathConstraint delegate;
  private final Snapshot snapshot;

  public ThreatAvoidanceConstraint(
    PathConstraint delegate,
    Snapshot snapshot
  ) {
    this.delegate = Objects.requireNonNull(delegate);
    this.snapshot = Objects.requireNonNull(snapshot);
  }

  @Override
  public GraphInstructions modifyAsNeeded(GraphInstructions instruction) {
    var modified = delegate.modifyAsNeeded(instruction);
    var addedCost = snapshot.threats().stream()
      .mapToDouble(threat -> exposureCost(
        modified.blockPosition().distance(threat.position()),
        threat.influenceRadius(),
        threat.maximumPenalty()
      ))
      .sum();
    return addedCost == 0
      ? modified
      : modified.withActionCost(modified.actionCost() + addedCost);
  }

  @Override
  public boolean allowsInstruction(
    GraphInstructions instruction,
    BlockGetter blockAccessor
  ) {
    if (!delegate.allowsInstruction(instruction, blockAccessor)) {
      return false;
    }
    var containingThreats = snapshot.threats().stream()
      .filter(threat -> snapshot.observerPosition()
        .distance(threat.position()) < threat.exclusionRadius())
      .count();
    for (var threat : snapshot.threats()) {
      var targetDistance = instruction.blockPosition()
        .distance(threat.position());
      if (targetDistance >= threat.exclusionRadius()) {
        continue;
      }
      var observerDistance = snapshot.observerPosition()
        .distance(threat.position());
      if (
        observerDistance >= threat.exclusionRadius()
          || (
          containingThreats <= 1
            && targetDistance + DISTANCE_TOLERANCE < observerDistance
        )
      ) {
        return false;
      }
    }
    return true;
  }

  @Override
  public PathConstraint delegate() {
    return delegate;
  }

  static double exposureCost(
    double distance,
    double influenceRadius,
    double maximumPenalty
  ) {
    if (distance >= influenceRadius || maximumPenalty == 0) {
      return 0;
    }
    var exposure = (influenceRadius - distance) / influenceRadius;
    return maximumPenalty * exposure * exposure;
  }

  public record Snapshot(
    SFVec3i observerPosition,
    List<Threat> threats
  ) {
    public Snapshot {
      Objects.requireNonNull(observerPosition);
      threats = List.copyOf(threats);
    }
  }

  public record Threat(
    SFVec3i position,
    double exclusionRadius,
    double influenceRadius,
    double maximumPenalty
  ) {
    public Threat {
      Objects.requireNonNull(position);
      if (!Double.isFinite(exclusionRadius) || exclusionRadius < 0) {
        throw new IllegalArgumentException(
          "Threat exclusion radius must be finite and non-negative"
        );
      }
      if (
        !Double.isFinite(influenceRadius)
          || influenceRadius < exclusionRadius
      ) {
        throw new IllegalArgumentException(
          "Threat influence radius must be finite and at least the exclusion radius"
        );
      }
      if (!Double.isFinite(maximumPenalty) || maximumPenalty < 0) {
        throw new IllegalArgumentException(
          "Threat maximum penalty must be finite and non-negative"
        );
      }
    }
  }
}
