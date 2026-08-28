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

import com.soulfiremc.server.pathfinding.RouteCost;
import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.execution.BlockBreakAction;
import com.soulfiremc.server.pathfinding.execution.BlockPlaceAction;
import com.soulfiremc.server.pathfinding.execution.JumpAndPlaceBelowAction;
import com.soulfiremc.server.pathfinding.execution.WorldAction;
import com.soulfiremc.server.pathfinding.graph.actions.movement.ActionDirection;
import lombok.With;

import java.util.List;

@With
public record GraphInstructions(
  SFVec3i blockPosition,
  int deltaUsableBlockItems,
  boolean requiresOneBlock,
  ActionDirection moveDirection,
  double actionCost,
  List<WorldAction> actions,
  double expectedDamage
) {
  public GraphInstructions(
    SFVec3i blockPosition,
    int deltaUsableBlockItems,
    boolean requiresOneBlock,
    ActionDirection moveDirection,
    double actionCost,
    List<WorldAction> actions
  ) {
    this(
      blockPosition,
      deltaUsableBlockItems,
      requiresOneBlock,
      moveDirection,
      actionCost,
      actions,
      0
    );
  }

  public RouteCost routeCost() {
    var placedBlocks = 0;
    var brokenBlocks = 0;
    for (var action : actions) {
      switch (action) {
        case BlockBreakAction _ -> brokenBlocks++;
        case BlockPlaceAction _, JumpAndPlaceBelowAction _ -> placedBlocks++;
        default -> {
        }
      }
    }
    return new RouteCost(
      expectedDamage,
      0,
      placedBlocks,
      brokenBlocks,
      actionCost
    );
  }
}
