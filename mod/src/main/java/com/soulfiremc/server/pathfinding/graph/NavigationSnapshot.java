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

import com.soulfiremc.server.pathfinding.NodeState;
import com.soulfiremc.server.pathfinding.ResourceState;
import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.SupportOrigin;
import com.soulfiremc.server.pathfinding.SupportSurface;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/// A lazy and stable view of the blocks read during one route search.
public final class NavigationSnapshot {
  private final BlockGetter blockAccessor;
  private final BlockGetter collisionAccessor;
  private final Long2ObjectOpenHashMap<BlockState> blockStates = new Long2ObjectOpenHashMap<>();
  private final Long2ObjectOpenHashMap<NavigationCell> cells = new Long2ObjectOpenHashMap<>();

  public NavigationSnapshot(BlockGetter blockAccessor) {
    this.blockAccessor = blockAccessor;
    this.collisionAccessor = new SnapshotBlockGetter();
  }

  public BlockState blockState(SFVec3i position) {
    return blockStates.computeIfAbsent(
      position.asMinecraftLong(),
      _ -> blockAccessor.getBlockState(position.toBlockPos())
    );
  }

  public NavigationCell cell(SFVec3i position) {
    return cells.computeIfAbsent(
      position.asMinecraftLong(),
      _ -> createCell(position, blockState(position))
    );
  }

  public NodeState stateAt(
    SFVec3i position,
    ResourceState resources,
    SupportOrigin supportOrigin
  ) {
    var cell = cell(position);
    var support = switch (cell.movementMode()) {
      case SWIMMING, CLIMBING -> SupportSurface.NONE;
      case GROUND -> cell.primarySupportSurface();
    };
    return new NodeState(
      position,
      support,
      supportOrigin,
      cell.movementMode(),
      resources
    );
  }

  private NavigationCell createCell(SFVec3i position, BlockState state) {
    return NavigationCell.create(
      state,
      state.getCollisionShape(collisionAccessor, position.toBlockPos())
    );
  }

  private final class SnapshotBlockGetter implements BlockGetter {
    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos position) {
      return blockAccessor.getBlockEntity(position);
    }

    @Override
    public BlockState getBlockState(BlockPos position) {
      return NavigationSnapshot.this.blockState(SFVec3i.fromInt(position));
    }

    @Override
    public FluidState getFluidState(BlockPos position) {
      return getBlockState(position).getFluidState();
    }

    @Override
    public int getHeight() {
      return blockAccessor.getHeight();
    }

    @Override
    public int getMinY() {
      return blockAccessor.getMinY();
    }
  }
}
