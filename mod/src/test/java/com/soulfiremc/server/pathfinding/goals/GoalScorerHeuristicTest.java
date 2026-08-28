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

import com.soulfiremc.server.pathfinding.BlockPlaceAgainstData;
import com.soulfiremc.server.pathfinding.NodeState;
import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.execution.JumpAndPlaceBelowAction;
import com.soulfiremc.server.pathfinding.graph.BlockFace;
import com.soulfiremc.test.utils.TestPathConstraint;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GoalScorerHeuristicTest {
  private static final SFVec3i ORIGIN = new SFVec3i(0, 64, 0);

  @Test
  void subtractsTheAcceptedRadiusFromPositionEstimates() {
    var position = new SFVec3i(10, 64, 0);

    assertEquals(
      7,
      new CloseToPosGoal(ORIGIN, 3)
        .computeScore(null, position, List.of())
    );
    assertEquals(
      8,
      new CloseToWorldPosGoal(new Vec3(0.5, 64, 0.5), 2)
        .computeScore(null, position, List.of())
    );
    assertEquals(
      8,
      new CloseToWorldXZGoal(0.5, 0.5, 2)
        .computeScore(null, position, List.of())
    );
  }

  @Test
  void leavesAnAdmissibleRadiusForActionCompletedGoals() {
    var position = new SFVec3i(10, 64, 0);

    assertEquals(
      7,
      new BreakBlockPosGoal(ORIGIN)
        .computeScore(null, position, List.of())
    );
    assertEquals(
      7,
      new PlaceBlockGoal(ORIGIN)
        .computeScore(null, position, List.of())
    );
  }

  @Test
  void pillarPlacementCanCompleteAPlaceGoal() {
    var action = new JumpAndPlaceBelowAction(
      ORIGIN,
      new BlockPlaceAgainstData(ORIGIN.add(0, -1, 0), BlockFace.TOP),
      TestPathConstraint.INSTANCE
    );

    assertTrue(new PlaceBlockGoal(ORIGIN).isFinished(
      new NodeState(ORIGIN.add(0, 1, 0), 0),
      List.of(action)
    ));
  }
}
