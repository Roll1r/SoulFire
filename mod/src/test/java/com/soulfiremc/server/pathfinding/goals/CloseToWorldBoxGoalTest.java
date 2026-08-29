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

import com.soulfiremc.server.pathfinding.SFVec3i;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CloseToWorldBoxGoalTest {
  @Test
  void scoresTheNearestPointOnALargeEntityHitbox() {
    var goal = new CloseToWorldBoxGoal(
      new AABB(4, 1, 0, 8, 4, 1),
      2.5,
      1.62
    );

    assertEquals(
      1,
      goal.computeScore(null, new SFVec3i(0, 0, 0), List.of())
    );
    assertEquals(
      0,
      goal.computeScore(null, new SFVec3i(1, 0, 0), List.of())
    );
  }

  @Test
  void includesVerticalReachFromThePlayersEye() {
    var goal = new CloseToWorldBoxGoal(
      new AABB(0, 5, 0, 1, 6, 1),
      2,
      1.62
    );

    assertEquals(
      1.38,
      goal.computeScore(null, new SFVec3i(0, 0, 0), List.of()),
      0.000_001
    );
  }
}
