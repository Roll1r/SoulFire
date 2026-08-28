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
import com.soulfiremc.server.util.SFBlockHelpers;
import com.soulfiremc.server.util.VectorHelper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.world.level.BlockGetter;

@Slf4j
public final class GapJumpAction implements WorldAction {
  private static final int MINIMUM_RUN_UP_TICKS = 2;
  private static final int MAXIMUM_RUN_UP_TICKS = 3;
  private static final double MINIMUM_FORWARD_SPEED = 0.08;

  private final SFVec3i startPosition;
  @Getter
  private final SFVec3i blockPosition;
  private int runUpTicks;
  private boolean startedJumping;

  public GapJumpAction(SFVec3i startPosition, SFVec3i blockPosition) {
    this.startPosition = startPosition;
    this.blockPosition = blockPosition;
  }

  @Override
  public boolean isCompleted(BotConnection connection) {
    var clientEntity = connection.minecraft().player;
    var botPosition = clientEntity.position();
    var level = connection.minecraft().level;

    var blockMeta = level.getBlockState(blockPosition.toBlockPos());
    var targetMiddleBlock = VectorHelper.topMiddleOfBlock(blockPosition, blockMeta);
    if (!MovementAction.hasReachedTargetHeight(
      botPosition.y,
      targetMiddleBlock.y,
      clientEntity.onGround()
        || clientEntity.isInWater()
        || clientEntity.isInLava()
        || clientEntity.onClimbable()
    )) {
      return false;
    }

    return MovementAction.horizontalDistance(botPosition, targetMiddleBlock) <= 0.3;
  }

  @Override
  public boolean isValid(BotConnection connection) {
    return MovementAction.hasValidTarget(connection, blockPosition)
      && hasClearTrajectory(
      connection.minecraft().level,
      startPosition,
      blockPosition
    );
  }

  static boolean hasClearTrajectory(
    BlockGetter level,
    SFVec3i start,
    SFVec3i target
  ) {
    var deltaX = target.x - start.x;
    var deltaZ = target.z - start.z;
    var steps = Math.max(Math.abs(deltaX), Math.abs(deltaZ));
    if (steps < 1 || start.y != target.y) {
      return false;
    }
    var stepX = Integer.signum(deltaX);
    var stepZ = Integer.signum(deltaZ);
    for (var step = 1; step < steps; step++) {
      var position = start.add(stepX * step, 0, stepZ * step);
      for (var y = 0; y <= 2; y++) {
        if (!SFBlockHelpers.isBlockFree(
          level.getBlockState(position.add(0, y, 0).toBlockPos())
        )) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public SFVec3i targetPosition(BotConnection connection) {
    return blockPosition;
  }

  @Override
  public void tick(BotConnection connection) {
    var clientEntity = connection.minecraft().player;
    connection.controlState().resetAll();

    var level = connection.minecraft().level;

    var blockMeta = level.getBlockState(blockPosition.toBlockPos());
    var targetMiddleBlock = VectorHelper.topMiddleOfBlock(blockPosition, blockMeta);

    connection.rotationControl().lookHorizontallyAt(targetMiddleBlock);

    connection.controlState().sprint(true);
    connection.controlState().up(true);
    if (!startedJumping && clientEntity.onGround()) {
      runUpTicks++;
      var velocity = VectorHelper.toVector2dXZ(clientEntity.getDeltaMovement());
      var targetDirection = VectorHelper.toVector2dXZ(
        targetMiddleBlock.subtract(clientEntity.position())
      );
      var forwardSpeed = velocity.equals(0, 0) || targetDirection.equals(0, 0)
        ? 0
        : velocity.dot(targetDirection.normalize());
      startedJumping = shouldStartJump(runUpTicks, forwardSpeed);
    }
    if (startedJumping) {
      connection.controlState().jump(true);
    }
  }

  static boolean shouldStartJump(int runUpTicks, double forwardSpeed) {
    return runUpTicks >= MAXIMUM_RUN_UP_TICKS
      || (runUpTicks >= MINIMUM_RUN_UP_TICKS && forwardSpeed >= MINIMUM_FORWARD_SPEED);
  }

  @Override
  public int getAllowedTicks() {
    // 5-seconds max to walk to a block
    return 5 * 20;
  }

  @Override
  public String toString() {
    return "GapJumpAction -> " + blockPosition.formatXYZ();
  }
}
