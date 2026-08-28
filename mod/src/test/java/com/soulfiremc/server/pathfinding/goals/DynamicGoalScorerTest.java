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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DynamicGoalScorerTest {
  @Test
  void freezesOneGoalForTheCompleteSearchSession() {
    var observations = new AtomicInteger();
    DynamicGoalScorer dynamic = () -> new PosGoal(
      new SFVec3i(observations.incrementAndGet(), 64, 0)
    );

    var snapshot = dynamic.snapshot();

    assertEquals(1, observations.get());
    assertTrue(snapshot.isFinished(
      new NodeState(new SFVec3i(1, 64, 0), 0),
      List.of()
    ));
    assertEquals(
      1,
      snapshot.computeScore(
        null,
        new SFVec3i(2, 64, 0),
        List.of()
      )
    );
    assertEquals(1, observations.get());
  }
}
