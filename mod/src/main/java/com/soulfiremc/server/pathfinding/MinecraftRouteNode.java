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

import com.soulfiremc.server.pathfinding.execution.WorldAction;
import com.soulfiremc.server.pathfinding.graph.actions.movement.ActionDirection;
import lombok.Getter;
import lombok.ToString;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

/// One immutable route label. A navigation state can have multiple labels when
/// cost and navigation resources form a Pareto frontier.
@Getter
@ToString
public final class MinecraftRouteNode implements Comparable<MinecraftRouteNode> {
  private final NodeState node;
  private final @Nullable MinecraftRouteNode parent;
  private final @Nullable ActionDirection parentToNodeDirection;
  private final List<WorldAction> actions;
  private final RouteCost routeCost;
  private final double targetCost;
  private final double heuristicWeight;

  public MinecraftRouteNode(
    NodeState node,
    @Nullable MinecraftRouteNode parent,
    @Nullable ActionDirection parentToNodeDirection,
    List<WorldAction> actions,
    RouteCost routeCost,
    double targetCost,
    double heuristicWeight
  ) {
    this.node = node;
    this.parent = parent;
    this.parentToNodeDirection = parentToNodeDirection;
    this.actions = List.copyOf(actions);
    this.routeCost = routeCost;
    this.targetCost = targetCost;
    this.heuristicWeight = heuristicWeight;
  }

  public MinecraftRouteNode(
    NodeState node,
    List<WorldAction> actions,
    double sourceCost,
    double targetCost,
    double totalRouteScore
  ) {
    this(
      node,
      null,
      null,
      actions,
      new RouteCost(0, 0, 0, 0, sourceCost),
      targetCost,
      inferWeight(sourceCost, targetCost, totalRouteScore)
    );
  }

  public double sourceCost() {
    return routeCost.durationCost();
  }

  public double totalRouteScore() {
    return routeCost.durationCost() + targetCost;
  }

  @Override
  public int compareTo(MinecraftRouteNode other) {
    return routeCost.compareEstimated(
      other.routeCost,
      targetCost * heuristicWeight,
      other.targetCost * other.heuristicWeight
    );
  }

  private static double inferWeight(
    double sourceCost,
    double targetCost,
    double totalRouteScore
  ) {
    if (targetCost == 0) {
      return 1;
    }
    return Math.max(0, (totalRouteScore - sourceCost) / targetCost);
  }
}
