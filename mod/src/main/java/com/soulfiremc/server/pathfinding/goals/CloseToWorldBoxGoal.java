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
package com.soulfiremc.server.pathfinding.goals;

import com.soulfiremc.server.pathfinding.NodeState;
import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.execution.WorldAction;
import com.soulfiremc.server.pathfinding.graph.MinecraftGraph;
import net.minecraft.world.phys.AABB;

import java.util.List;

/// Goal to move the player's eye within a radius of a world-space box.
///
/// Combat uses this instead of an entity origin because large and multipart
/// entities can have origins that no standing player can reach.
public record CloseToWorldBoxGoal(
  AABB box,
  double maxRadius,
  double eyeHeight
) implements GoalScorer {
  @Override
  public double computeScore(
    MinecraftGraph graph,
    SFVec3i blockPosition,
    List<WorldAction> actions
  ) {
    return Math.max(0, distanceToPlayerEye(blockPosition) - maxRadius);
  }

  @Override
  public boolean isFinished(NodeState state, List<WorldAction> actions) {
    return distanceToPlayerEye(state.blockPosition()) <= maxRadius;
  }

  double distanceToPlayerEye(SFVec3i blockPosition) {
    var x = blockPosition.x + 0.5;
    var y = blockPosition.y + eyeHeight;
    var z = blockPosition.z + 0.5;
    var deltaX = Math.max(box.minX - x, Math.max(0, x - box.maxX));
    var deltaY = Math.max(box.minY - y, Math.max(0, y - box.maxY));
    var deltaZ = Math.max(box.minZ - z, Math.max(0, z - box.maxZ));
    return Math.sqrt(
      deltaX * deltaX
        + deltaY * deltaY
        + deltaZ * deltaZ
    );
  }
}
