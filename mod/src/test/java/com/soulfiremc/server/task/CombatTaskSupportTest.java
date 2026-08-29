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

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CombatTaskSupportTest {
  @Test
  void rejectsMultipartPositionsDetachedFromTheirParent() {
    assertTrue(CombatTaskSupport.isPlausiblePartPosition(
      new Vec3(80, 52, 0),
      new Vec3(74, 51, 0)
    ));
    assertFalse(CombatTaskSupport.isPlausiblePartPosition(
      new Vec3(80, 52, 0),
      Vec3.ZERO
    ));
  }

  @Test
  void pursuesTheDragonOnlyDuringMeleePhases() {
    assertTrue(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.LANDING
    ));
    assertTrue(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.HOVERING
    ));
    assertTrue(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.SITTING_FLAMING
    ));
    assertTrue(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.SITTING_SCANNING
    ));
    assertTrue(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.SITTING_ATTACKING
    ));
    assertFalse(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.LANDING_APPROACH
    ));
    assertFalse(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.TAKEOFF
    ));
    assertFalse(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.HOLDING_PATTERN
    ));
    assertFalse(CombatTaskSupport.isDragonMeleePhase(
      EnderDragonPhase.CHARGING_PLAYER
    ));
  }

  @Test
  void anchorsDragonMeleeAtTheExitPodium() {
    var approach = CombatTaskSupport.dragonMeleeApproach(
      new BlockPos(0, 65, 0)
    );

    assertEquals(new Vec3(0.5, 61, 0.5), approach.position());
    assertEquals(-1.5, approach.box().minX);
    assertEquals(61, approach.box().minY);
    assertEquals(-1.5, approach.box().minZ);
    assertEquals(2.5, approach.box().maxX);
    assertEquals(62, approach.box().maxY);
    assertEquals(2.5, approach.box().maxZ);
  }

  @Test
  void stagesNearTheExitPodiumWhileTheDragonCircles() {
    var approaches = CombatTaskSupport.dragonWaitingApproaches(
      new BlockPos(128, 70, -64)
    );
    var approach = approaches.getLast();

    assertEquals(List.of(72.0, 48.0, 32.0), approaches.stream()
      .map(candidate -> candidate.goal().maxRadius())
      .toList());
    assertEquals(new Vec3(128.5, 70, -63.5), approach.position());
    assertEquals(128.5, approach.goal().x());
    assertEquals(-63.5, approach.goal().z());
    assertEquals(32, approach.goal().maxRadius());
  }
}
