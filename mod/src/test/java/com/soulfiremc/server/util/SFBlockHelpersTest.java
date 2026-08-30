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
package com.soulfiremc.server.util;

import com.soulfiremc.test.utils.TestBlockAccessorBuilder;
import com.soulfiremc.test.utils.TestBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SFBlockHelpersTest {
  private static final BlockPos FLOOR = new BlockPos(0, 2, 0);

  @BeforeAll
  static void setup() {
    TestBootstrap.bootstrapForTest();
  }

  @Test
  void acceptsOrdinaryAndSupportedGravityFloors() {
    var stoneFloor = new TestBlockAccessorBuilder();
    stoneFloor.setBlockAt(0, 2, 0, Blocks.STONE);

    assertTrue(SFBlockHelpers.isStableWalkableFloorBlock(
      stoneFloor.build(),
      FLOOR,
      Blocks.STONE.defaultBlockState()
    ));

    var supportedGravel = new TestBlockAccessorBuilder();
    supportedGravel.setBlockAt(0, 1, 0, Blocks.NETHERRACK);
    supportedGravel.setBlockAt(0, 2, 0, Blocks.GRAVEL);

    assertTrue(SFBlockHelpers.isStableWalkableFloorBlock(
      supportedGravel.build(),
      FLOOR,
      Blocks.GRAVEL.defaultBlockState()
    ));
  }

  @Test
  void rejectsSuspendedGravityFloorsOverAirOrLava() {
    var overAir = new TestBlockAccessorBuilder();
    overAir.setBlockAt(0, 2, 0, Blocks.GRAVEL);

    assertFalse(SFBlockHelpers.isStableWalkableFloorBlock(
      overAir.build(),
      FLOOR,
      Blocks.GRAVEL.defaultBlockState()
    ));

    var overLava = new TestBlockAccessorBuilder();
    overLava.setBlockAt(0, 1, 0, Blocks.LAVA);
    overLava.setBlockAt(0, 2, 0, Blocks.GRAVEL);

    assertFalse(SFBlockHelpers.isStableWalkableFloorBlock(
      overLava.build(),
      FLOOR,
      Blocks.GRAVEL.defaultBlockState()
    ));
  }

  @Test
  void rejectsAGravityColumnWhoseBottomBlockWillFall() {
    var blocks = new TestBlockAccessorBuilder();
    blocks.setBlockAt(0, 1, 0, Blocks.GRAVEL);
    blocks.setBlockAt(0, 2, 0, Blocks.SAND);

    assertFalse(SFBlockHelpers.isStableWalkableFloorBlock(
      blocks.build(),
      FLOOR,
      Blocks.SAND.defaultBlockState()
    ));
  }
}
