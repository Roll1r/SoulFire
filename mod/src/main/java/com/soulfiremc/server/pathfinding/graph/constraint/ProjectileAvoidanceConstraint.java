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
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

/// Keeps escape routes outside the future swept paths of visible projectiles.
///
/// The trajectory is frozen for one ARA* search. This keeps transition costs
/// stable while that search repairs its incumbent. A caller can replace the
/// search when a newly fired projectile appears.
public final class ProjectileAvoidanceConstraint
  implements DelegatePathConstraint {
  static final double HORIZONTAL_CLEARANCE = 2;
  static final double VERTICAL_CLEARANCE = 2.2;
  private static final double HARD_CLEARANCE = 1;
  private static final double INFLUENCE_CLEARANCE = 2;
  private static final double MAXIMUM_EXPOSURE_PENALTY = 128;
  private static final double CLEARANCE_TOLERANCE = 0.05;

  private final PathConstraint delegate;
  private final Snapshot snapshot;

  public ProjectileAvoidanceConstraint(
    PathConstraint delegate,
    Snapshot snapshot
  ) {
    this.delegate = Objects.requireNonNull(delegate);
    this.snapshot = Objects.requireNonNull(snapshot);
  }

  @Override
  public GraphInstructions modifyAsNeeded(GraphInstructions instruction) {
    var modified = delegate.modifyAsNeeded(instruction);
    var center = playerCenter(instruction.blockPosition());
    var addedCost = snapshot.trajectories().stream()
      .mapToDouble(trajectory -> exposureCost(clearance(center, trajectory)))
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
    var targetCenter = playerCenter(instruction.blockPosition());
    for (var trajectory : snapshot.trajectories()) {
      var targetClearance = clearance(targetCenter, trajectory);
      if (targetClearance >= HARD_CLEARANCE) {
        continue;
      }
      var observerClearance = clearance(
        snapshot.observerCenter(),
        trajectory
      );
      if (
        observerClearance >= HARD_CLEARANCE
          || targetClearance + CLEARANCE_TOLERANCE < observerClearance
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

  static Vec3 playerCenter(SFVec3i feetPosition) {
    return new Vec3(
      feetPosition.x + 0.5,
      feetPosition.y + 0.9,
      feetPosition.z + 0.5
    );
  }

  static double clearance(Vec3 playerCenter, Trajectory trajectory) {
    return trajectory.segments().stream()
      .mapToDouble(segment -> scaledDistanceToSegment(playerCenter, segment))
      .min()
      .orElse(Double.POSITIVE_INFINITY);
  }

  static double exposureCost(double clearance) {
    if (clearance >= INFLUENCE_CLEARANCE) {
      return 0;
    }
    var exposure = (INFLUENCE_CLEARANCE - clearance)
      / INFLUENCE_CLEARANCE;
    return MAXIMUM_EXPOSURE_PENALTY * exposure * exposure;
  }

  private static double scaledDistanceToSegment(
    Vec3 point,
    Segment segment
  ) {
    var pointX = point.x / HORIZONTAL_CLEARANCE;
    var pointY = point.y / VERTICAL_CLEARANCE;
    var pointZ = point.z / HORIZONTAL_CLEARANCE;
    var startX = segment.start().x / HORIZONTAL_CLEARANCE;
    var startY = segment.start().y / VERTICAL_CLEARANCE;
    var startZ = segment.start().z / HORIZONTAL_CLEARANCE;
    var deltaX = segment.end().x / HORIZONTAL_CLEARANCE - startX;
    var deltaY = segment.end().y / VERTICAL_CLEARANCE - startY;
    var deltaZ = segment.end().z / HORIZONTAL_CLEARANCE - startZ;
    var lengthSquared = deltaX * deltaX
      + deltaY * deltaY
      + deltaZ * deltaZ;
    if (lengthSquared < 1.0E-9) {
      return Math.sqrt(
        Mth.square(pointX - startX)
          + Mth.square(pointY - startY)
          + Mth.square(pointZ - startZ)
      );
    }
    var fraction = Mth.clamp(
      ((pointX - startX) * deltaX
        + (pointY - startY) * deltaY
        + (pointZ - startZ) * deltaZ) / lengthSquared,
      0,
      1
    );
    var closestX = startX + deltaX * fraction;
    var closestY = startY + deltaY * fraction;
    var closestZ = startZ + deltaZ * fraction;
    return Math.sqrt(
      Mth.square(pointX - closestX)
        + Mth.square(pointY - closestY)
        + Mth.square(pointZ - closestZ)
    );
  }

  public record Snapshot(
    Vec3 observerCenter,
    List<Trajectory> trajectories
  ) {
    public Snapshot {
      Objects.requireNonNull(observerCenter);
      trajectories = List.copyOf(trajectories);
    }
  }

  public record Trajectory(int entityId, List<Segment> segments) {
    public Trajectory {
      segments = List.copyOf(segments);
      if (segments.isEmpty()) {
        throw new IllegalArgumentException(
          "A projectile trajectory must contain at least one segment"
        );
      }
    }
  }

  public record Segment(Vec3 start, Vec3 end) {
    public Segment {
      Objects.requireNonNull(start);
      Objects.requireNonNull(end);
    }
  }
}
