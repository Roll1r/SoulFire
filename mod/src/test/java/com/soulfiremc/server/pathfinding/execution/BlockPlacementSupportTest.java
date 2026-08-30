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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BlockPlacementSupportTest {
  private static final BlockPos TARGET = new BlockPos(10, 64, 10);

  @Test
  void detectsCurrentPlayerIntersection() {
    var player = playerBounds(new Vec3(10.5, 64, 9.75));

    assertTrue(BlockPlacementSupport.requiresPlayerClearance(
      player,
      Vec3.ZERO,
      TARGET
    ));
  }

  @Test
  void reservesClearanceForResidualMotion() {
    var player = playerBounds(new Vec3(10.5, 64, 9.6));

    assertFalse(BlockPlacementSupport.requiresPlayerClearance(
      player,
      Vec3.ZERO,
      TARGET
    ));
    assertTrue(BlockPlacementSupport.requiresPlayerClearance(
      player,
      new Vec3(0, 0, 0.08),
      TARGET
    ));
  }

  @Test
  void choosesTheNearestAxisAlignedClearancePosition() {
    var position = new Vec3(10.5, 64, 9.75);
    var clearance = BlockPlacementSupport.nearestClearancePosition(
      playerBounds(position),
      position,
      TARGET
    );

    assertEquals(10.5, clearance.x, 1.0E-9);
    assertTrue(clearance.z < 9.7);
    assertFalse(BlockPlacementSupport.requiresPlayerClearance(
      playerBounds(clearance),
      Vec3.ZERO,
      TARGET
    ));
  }

  @Test
  void checksThePlannedFaceBeforeAlternateFaces() {
    var target = new BlockPos(3, 8, -4);
    var preferredAgainst = target.below();

    var faces = BlockPlacementSupport.orderedFaces(
      target,
      preferredAgainst,
      Direction.UP,
      new Vec3(3.5, 9.62, -6.5)
    );

    assertEquals(Direction.UP, faces.getFirst());
    assertEquals(6, faces.size());
    assertEquals(6, faces.stream().distinct().count());
  }

  @Test
  void waitsForPlacementMomentumToSettle() {
    assertTrue(BlockPlacementSupport.isHorizontallyStable(
      new Vec3(0.01, -0.1, 0.01)
    ));
    assertFalse(BlockPlacementSupport.isHorizontallyStable(
      new Vec3(0.03, 0, 0)
    ));
  }

  @Test
  void searchesPlacementViewsNearestFirst() {
    var position = new Vec3(8.6, 67, -10.6);
    var target = new BlockPos(7, 68, -10);
    var candidates = BlockPlacementSupport.placementViewCandidates(
      position,
      target
    );

    assertEquals(position, candidates.getFirst());
    assertNotEquals(position, candidates.getLast());
    for (var index = 1; index < candidates.size(); index++) {
      assertTrue(
        candidates.get(index - 1).distanceToSqr(position)
          <= candidates.get(index).distanceToSqr(position)
      );
    }
  }

  @Test
  void samplesBothSidesOfAPlacementTarget() {
    var position = new Vec3(0.5, 65, 0.5);
    var target = new BlockPos(1, 64, 0);
    var candidates = BlockPlacementSupport.placementViewCandidates(
      position,
      target
    );

    assertTrue(candidates.stream().anyMatch(candidate -> candidate.x < 0));
    assertTrue(candidates.stream().anyMatch(candidate -> candidate.x > 3));
    assertTrue(candidates.stream().allMatch(candidate ->
      candidate.y == position.y));
  }

  private static AABB playerBounds(Vec3 position) {
    return new AABB(
      position.x - 0.3,
      position.y,
      position.z - 0.3,
      position.x + 0.3,
      position.y + 1.8,
      position.z + 0.3
    );
  }
}
