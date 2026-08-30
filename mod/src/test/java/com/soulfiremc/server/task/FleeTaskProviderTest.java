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
package com.soulfiremc.server.task;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleeTaskProviderTest {
  @Test
  void strikesReachablePursuerAtFullCooldown() {
    assertTrue(FleeTaskProvider.shouldDefensivelyStrike(
      true,
      false,
      true,
      3,
      1
    ));
  }

  @Test
  void refusesUnsafeOrInvalidDefensiveStrikes() {
    assertFalse(FleeTaskProvider.shouldDefensivelyStrike(
      true,
      true,
      true,
      2,
      1
    ));
    assertFalse(FleeTaskProvider.shouldDefensivelyStrike(
      true,
      false,
      false,
      2,
      1
    ));
    assertFalse(FleeTaskProvider.shouldDefensivelyStrike(
      true,
      false,
      true,
      3.01,
      1
    ));
    assertFalse(FleeTaskProvider.shouldDefensivelyStrike(
      true,
      false,
      true,
      2,
      0.99F
    ));
    assertFalse(FleeTaskProvider.shouldDefensivelyStrike(
      false,
      false,
      true,
      2,
      1
    ));
  }

  @Test
  void strikesOnlyAlreadyAggressiveGroupMembersWhileFleeing() {
    assertTrue(FleeTaskProvider.isGroupAggroEntityType(EntityTypes.PIGLIN));
    assertTrue(FleeTaskProvider.isGroupAggroEntityType(
      EntityTypes.PIGLIN_BRUTE
    ));
    assertTrue(FleeTaskProvider.isGroupAggroEntityType(
      EntityTypes.ZOMBIFIED_PIGLIN
    ));
    assertFalse(FleeTaskProvider.isGroupAggroEntityType(EntityTypes.ZOMBIE));

    assertTrue(FleeTaskProvider.shouldExcludeDefensiveStrike(
      EntityTypes.ZOMBIFIED_PIGLIN,
      false
    ));
    assertFalse(FleeTaskProvider.shouldExcludeDefensiveStrike(
      EntityTypes.ZOMBIFIED_PIGLIN,
      true
    ));
    assertTrue(FleeTaskProvider.shouldExcludeDefensiveStrike(
      EntityTypes.PIGLIN,
      false
    ));
    assertFalse(FleeTaskProvider.shouldExcludeDefensiveStrike(
      EntityTypes.PIGLIN,
      true
    ));
    assertFalse(FleeTaskProvider.shouldExcludeDefensiveStrike(
      EntityTypes.ZOMBIE,
      false
    ));
    assertTrue(FleeTaskProvider.shouldExcludeDefensiveStrike(
      EntityTypes.CREEPER,
      true
    ));
    assertTrue(FleeTaskProvider.shouldExcludeDefensiveStrike(
      EntityTypes.ENDERMAN,
      true
    ));
  }

  @Test
  void detectsAnArrowWhoseTrajectoryCrossesThePlayer() {
    var dodge = FleeTaskProvider.incomingProjectileDodge(
      Vec3.ZERO,
      new Vec3(1, 0, 0),
      new Vec3(5, 0, 0),
      0,
      2
    );

    assertTrue(dodge != null && dodge.left());
  }

  @Test
  void ignoresArrowsMovingAwayOrMissingThePlayer() {
    assertNull(FleeTaskProvider.incomingProjectileDodge(
      Vec3.ZERO,
      new Vec3(-1, 0, 0),
      new Vec3(5, 0, 0),
      0,
      0
    ));
    assertNull(FleeTaskProvider.incomingProjectileDodge(
      Vec3.ZERO,
      new Vec3(1, 0, 0),
      new Vec3(5, 0, 4),
      0,
      0
    ));
  }
}
