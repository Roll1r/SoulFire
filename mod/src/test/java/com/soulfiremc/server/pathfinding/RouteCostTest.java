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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RouteCostTest {
  @Test
  void prefersSafeRoutesBeforeShortRoutes() {
    var safe = new RouteCost(0, 0, 0, 0, 20);
    var dangerous = new RouteCost(1, 0, 0, 0, 1);

    assertTrue(safe.compareTo(dangerous) < 0);
  }

  @Test
  void rawBlockUseDoesNotPreventAUsefulShorterRoute() {
    var placed = new RouteCost(0, 0, 1, 0, 5);
    var noPlacement = new RouteCost(0, 0, 0, 0, 20);

    assertTrue(placed.compareTo(noPlacement) < 0);
  }

  @Test
  void dominanceKeepsCostAndResourceTradeoffs() {
    var faster = new RouteCost(0, 0, 1, 0, 5);
    var conservative = new RouteCost(0, 0, 0, 0, 6);

    assertFalse(faster.noWorseThan(conservative));
    assertFalse(conservative.noWorseThan(faster));
  }
}
