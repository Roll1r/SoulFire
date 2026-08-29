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
package com.soulfiremc.server.pathfinding.execution;

import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.test.utils.TestBlockAccessorBuilder;
import com.soulfiremc.test.utils.TestBootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GapJumpActionTest {
  @BeforeAll
  static void setup() {
    TestBootstrap.bootstrapForTest();
  }

  @Test
  void jumpsAfterRunningPastThePlayersHalfWidth() {
    assertFalse(GapJumpAction.shouldStartJump(0.29, 0.6));
    assertTrue(GapJumpAction.shouldStartJump(0.3, 0.6));
    assertTrue(GapJumpAction.shouldStartJump(0.31, 0.6));
  }

  @Test
  void rejectsAChangedBlockInsideTheJumpTrajectory() {
    var clear = new TestBlockAccessorBuilder().build();
    var blockedBuilder = new TestBlockAccessorBuilder();
    blockedBuilder.setBlockAt(1, 2, 0, Blocks.STONE);
    var start = SFVec3i.from(0, 1, 0);
    var target = SFVec3i.from(3, 1, 0);

    assertTrue(GapJumpAction.hasClearTrajectory(clear, start, target));
    assertFalse(GapJumpAction.hasClearTrajectory(
      blockedBuilder.build(),
      start,
      target
    ));
  }

  @Test
  void rejectsAnObstructionBeyondTheLanding() {
    var blockedBuilder = new TestBlockAccessorBuilder();
    blockedBuilder.setBlockAt(4, 1, 0, Blocks.STONE);
    var start = SFVec3i.from(0, 1, 0);
    var target = SFVec3i.from(3, 1, 0);

    assertFalse(GapJumpAction.hasClearTrajectory(
      blockedBuilder.build(),
      start,
      target
    ));
  }
}
