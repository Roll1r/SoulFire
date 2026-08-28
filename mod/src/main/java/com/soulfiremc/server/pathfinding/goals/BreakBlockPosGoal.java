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
import com.soulfiremc.server.pathfinding.execution.BlockBreakAction;
import com.soulfiremc.server.pathfinding.execution.WorldAction;
import com.soulfiremc.server.pathfinding.graph.MinecraftGraph;

import java.util.List;

public record BreakBlockPosGoal(SFVec3i goal) implements GoalScorer {
  /// Current graph primitives only mine blocks within three blocks of the
  /// source feet position. Subtracting the full radius keeps this estimate
  /// admissible for transition-completed goals.
  private static final double MAXIMUM_ACTION_OFFSET = 3;

  @Override
  public double computeScore(MinecraftGraph graph, SFVec3i blockPosition, List<WorldAction> actions) {
    return Math.max(
      0,
      blockPosition.distance(goal) - MAXIMUM_ACTION_OFFSET
    );
  }

  @Override
  public boolean isFinished(NodeState state, List<WorldAction> actions) {
    for (var action : actions) {
      if (action instanceof BlockBreakAction breakBlockAction && breakBlockAction.blockPosition().equals(goal)) {
        return true;
      }
    }

    return false;
  }
}
