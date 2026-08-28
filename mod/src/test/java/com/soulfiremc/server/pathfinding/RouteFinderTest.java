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

import com.soulfiremc.server.pathfinding.execution.RecalculatePathAction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RouteFinderTest {
  @Test
  void partialRoutesCarryAnExplicitFrontierReason() {
    var metadata = metadata(RouteFinder.FrontierReason.LEVEL_BOUNDARY);
    var result = new RouteFinder.PartialRouteResult(
      List.of(new RecalculatePathAction()),
      new SFVec3i(1, 64, 0),
      metadata
    );

    assertEquals(
      RouteFinder.FrontierReason.LEVEL_BOUNDARY,
      result.metadata().frontierReason()
    );
  }

  @Test
  void partialRoutesPreferEfficientChunkBoundariesOverLongDetours() {
    var efficientBoundary = new MinecraftRouteNode(
      new NodeState(new SFVec3i(1, 64, 0), 0),
      List.of(),
      1,
      99,
      100
    );
    var closerAfterDetour = new MinecraftRouteNode(
      new NodeState(new SFVec3i(10, 64, 0), 0),
      List.of(),
      80,
      90,
      170
    );

    assertTrue(RouteFinder.comparePartialRouteCandidates(
      efficientBoundary,
      closerAfterDetour
    ) < 0);
    assertTrue(RouteFinder.comparePartialRouteCandidates(
      closerAfterDetour,
      efficientBoundary
    ) > 0);
  }

  @Test
  void partialRoutesRejectChunkBoundariesThatMoveAwayFromTheGoal() {
    var start = routeNode(null, new SFVec3i(0, 64, 0), 0, 32);
    var progressingBoundary = routeNode(
      start,
      new SFVec3i(1, 64, 0),
      8,
      31
    );
    var regressingBoundary = routeNode(
      start,
      new SFVec3i(-64, 64, 0),
      68,
      94
    );

    assertTrue(RouteFinder.isProgressingPartialRoute(
      32,
      progressingBoundary
    ));
    assertFalse(RouteFinder.isProgressingPartialRoute(
      32,
      regressingBoundary
    ));
  }

  private static MinecraftRouteNode routeNode(
    MinecraftRouteNode parent,
    SFVec3i position,
    double sourceCost,
    double targetCost
  ) {
    return new MinecraftRouteNode(
      new NodeState(position, 0),
      parent,
      null,
      List.of(),
      new RouteCost(0, 0, 0, 0, sourceCost),
      targetCost,
      1
    );
  }

  private static RouteFinder.RouteSearchMetadata metadata(
    RouteFinder.FrontierReason frontierReason
  ) {
    return new RouteFinder.RouteSearchMetadata(
      RouteSearchMode.NORMAL,
      RouteSearchMode.NORMAL.heuristicWeight(),
      RouteCost.ZERO,
      0,
      0,
      0,
      frontierReason
    );
  }
}
