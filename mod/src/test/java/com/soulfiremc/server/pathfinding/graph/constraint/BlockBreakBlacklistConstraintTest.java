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
package com.soulfiremc.server.pathfinding.graph.constraint;

import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.test.utils.TestBootstrap;
import com.soulfiremc.test.utils.TestPathConstraint;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockBreakBlacklistConstraintTest {
  @BeforeAll
  static void bootstrapMinecraft() {
    TestBootstrap.bootstrapForTest();
  }

  @Test
  void onlyPreventsBreakingExplicitlyProtectedPositions() {
    var protectedPosition = SFVec3i.from(4, 65, -2);
    var constraint = new BlockBreakBlacklistConstraint(
      TestPathConstraint.INSTANCE,
      Set.of(protectedPosition)
    );

    assertFalse(constraint.canBreakBlock(
      protectedPosition,
      Blocks.STONE.defaultBlockState()
    ));
    assertTrue(constraint.canBreakBlock(
      SFVec3i.from(5, 65, -2),
      Blocks.STONE.defaultBlockState()
    ));
  }
}
