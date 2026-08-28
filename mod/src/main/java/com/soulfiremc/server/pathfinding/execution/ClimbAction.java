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

import com.soulfiremc.server.bot.BotConnection;
import com.soulfiremc.server.pathfinding.SFVec3i;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/// Climbs one block in a ladder, vine, or scaffolding column.
public final class ClimbAction implements WorldAction {
  private static final double MAXIMUM_HORIZONTAL_ERROR = 0.45;
  private final SFVec3i blockPosition;
  private final boolean ascending;

  public ClimbAction(SFVec3i blockPosition, boolean ascending) {
    this.blockPosition = blockPosition;
    this.ascending = ascending;
  }

  public SFVec3i blockPosition() {
    return blockPosition;
  }

  @Override
  public boolean isCompleted(BotConnection connection) {
    var position = connection.minecraft().player.position();
    var target = Vec3.atBottomCenterOf(blockPosition.toBlockPos());
    var reachedHeight = ascending
      ? position.y >= target.y - 0.15
      : position.y <= target.y + 0.15;
    return reachedHeight
      && MovementAction.horizontalDistance(position, target)
        <= MAXIMUM_HORIZONTAL_ERROR;
  }

  @Override
  public boolean isValid(BotConnection connection) {
    var level = connection.minecraft().level;
    var source = ascending
      ? blockPosition.sub(0, 1, 0)
      : blockPosition.add(0, 1, 0);
    return isClimbable(level.getBlockState(source.toBlockPos()))
      && isClimbable(level.getBlockState(blockPosition.toBlockPos()));
  }

  private static boolean isClimbable(
    net.minecraft.world.level.block.state.BlockState state
  ) {
    return state.is(BlockTags.CLIMBABLE)
      || state.is(Blocks.LADDER)
      || state.is(Blocks.SCAFFOLDING);
  }

  @Override
  public SFVec3i targetPosition(BotConnection connection) {
    return blockPosition;
  }

  @Override
  public void tick(BotConnection connection) {
    var player = connection.minecraft().player;
    var controls = connection.controlState();
    controls.resetAll();

    var target = Vec3.atBottomCenterOf(blockPosition.toBlockPos());
    var movementInput = MovementAction.movementInputFor(
      player.position(),
      player.getYRot(),
      target
    );
    controls.up(movementInput.forward());
    controls.down(movementInput.backward());
    controls.left(movementInput.left());
    controls.right(movementInput.right());

    if (ascending) {
      controls.jump(true);
      return;
    }

    var state = connection.minecraft().level.getBlockState(
      player.blockPosition()
    );
    controls.shift(state.is(Blocks.SCAFFOLDING));
  }

  @Override
  public int getAllowedTicks() {
    return 5 * 20;
  }

  @Override
  public String toString() {
    return "ClimbAction -> " + blockPosition.formatXYZ();
  }
}
