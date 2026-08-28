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
package com.soulfiremc.server.pathfinding.graph;

import com.soulfiremc.server.pathfinding.MovementMode;
import com.soulfiremc.server.pathfinding.SupportSurface;
import com.soulfiremc.server.util.SFBlockHelpers;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.List;

/// Collision and movement data derived for one block that can contain the
/// player's feet.
public record NavigationCell(
  BlockState blockState,
  List<SupportSurface> supportSurfaces,
  MovementMode movementMode,
  boolean bodyPassable,
  boolean hazardous
) {
  public static NavigationCell create(
    BlockState state,
    net.minecraft.world.phys.shapes.VoxelShape collisionShape
  ) {
    var supportSurfaces = collisionShape.toAabbs().stream()
      .map(SupportSurface::fromBox)
      .distinct()
      .sorted(Comparator.comparingInt(SupportSurface::height).reversed())
      .toList();
    return new NavigationCell(
      state,
      supportSurfaces,
      movementMode(state),
      SFBlockHelpers.isBodyPassableBlock(state),
      SFBlockHelpers.isHurtOnTouchSide(state)
        || SFBlockHelpers.isHurtWhenStoodOn(state)
    );
  }

  public SupportSurface primarySupportSurface() {
    return supportSurfaces.stream()
      .filter(surface -> surface.contains(0.5, 0.5))
      .findFirst()
      .orElseGet(() -> supportSurfaces.stream()
        .findFirst()
        .orElse(SupportSurface.FLOOR));
  }

  private static MovementMode movementMode(BlockState state) {
    if (SFBlockHelpers.isSwimmableWaterBlock(state)) {
      return MovementMode.SWIMMING;
    }
    if (
      state.is(BlockTags.CLIMBABLE)
        || state.is(Blocks.LADDER)
        || state.is(Blocks.SCAFFOLDING)
    ) {
      return MovementMode.CLIMBING;
    }
    return MovementMode.GROUND;
  }
}
