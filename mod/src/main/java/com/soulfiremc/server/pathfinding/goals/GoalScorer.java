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

import java.util.List;

/// A goal represents something that the user wants the bot to achieve.
public interface GoalScorer {
  /// Calculates an admissible lower bound on the remaining block displacement
  /// to the goal. The estimate must never exceed the displacement required by
  /// the cheapest valid route. Return zero when no tighter bound is known.
  ///
  /// @param graph         the graph to calculate the score for
  /// @param blockPosition the block position to calculate the score for
  /// @param actions       the actions that have been executed to reach the current state
  /// @return a finite, non-negative lower bound in blocks
  double computeScore(MinecraftGraph graph, SFVec3i blockPosition, List<WorldAction> actions);

  /// Checks if the given world state indicates that the goal is reached.
  ///
  /// @param state   the navigation state to check
  /// @param actions the transition actions that entered the state
  /// @return true if the goal is reached, false otherwise
  boolean isFinished(NodeState state, List<WorldAction> actions);

  /// Freezes dynamic goal observations for one immutable search session.
  default GoalScorer snapshot() {
    return this;
  }
}
