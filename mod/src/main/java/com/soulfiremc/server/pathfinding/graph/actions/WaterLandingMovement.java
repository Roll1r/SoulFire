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
package com.soulfiremc.server.pathfinding.graph.actions;

import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.execution.MovementAction;
import com.soulfiremc.server.pathfinding.graph.GraphInstructions;
import com.soulfiremc.server.pathfinding.graph.MinecraftGraph;
import com.soulfiremc.server.pathfinding.graph.actions.movement.ActionDirection;
import com.soulfiremc.server.pathfinding.graph.actions.movement.SkyDirection;
import com.soulfiremc.server.util.SFBlockHelpers;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Consumer;

/// Steps from a ledge into a verified water landing column.
public final class WaterLandingMovement extends GraphAction implements Cloneable {
  private static final int MINIMUM_FALL_HEIGHT = 4;
  private static final int MAXIMUM_FALL_HEIGHT = 8;
  private final SkyDirection direction;
  private final int fallHeight;
  private final SFVec3i targetFeetBlock;

  private WaterLandingMovement(
    SkyDirection direction,
    int fallHeight,
    SubscriptionConsumer blockSubscribers
  ) {
    super(actionDirection(direction));
    this.direction = direction;
    this.fallHeight = fallHeight;
    this.targetFeetBlock = direction.offset(
      SFVec3i.from(0, -fallHeight, 0)
    );

    for (var y = -fallHeight + 1; y <= 1; y++) {
      blockSubscribers.subscribe(
        direction.offset(SFVec3i.from(0, y, 0)),
        FreeColumnSubscription.INSTANCE
      );
    }
    blockSubscribers.subscribe(
      targetFeetBlock,
      WaterSubscription.INSTANCE
    );
    blockSubscribers.subscribe(
      targetFeetBlock.sub(0, 1, 0),
      SafeFloorSubscription.INSTANCE
    );
  }

  public static void registerWaterLandingMovements(
    Consumer<GraphAction> callback,
    SubscriptionConsumer blockSubscribers
  ) {
    for (var direction : List.of(
      SkyDirection.NORTH,
      SkyDirection.SOUTH,
      SkyDirection.EAST,
      SkyDirection.WEST
    )) {
      for (
        var height = MINIMUM_FALL_HEIGHT;
        height <= MAXIMUM_FALL_HEIGHT;
        height++
      ) {
        callback.accept(new WaterLandingMovement(
          direction,
          height,
          blockSubscribers
        ));
      }
    }
  }

  @Override
  public List<GraphInstructions> getInstructions(
    MinecraftGraph graph,
    SFVec3i node
  ) {
    var target = node.add(targetFeetBlock);
    return List.of(new GraphInstructions(
      target,
      0,
      false,
      actionDirection,
      fallHeight,
      List.of(new MovementAction(target, false, graph.pathConstraint()))
    ));
  }

  @Override
  public WaterLandingMovement copy() {
    return clone();
  }

  @Override
  public WaterLandingMovement clone() {
    try {
      return (WaterLandingMovement) super.clone();
    } catch (CloneNotSupportedException _) {
      throw new InternalError();
    }
  }

  private static ActionDirection actionDirection(SkyDirection direction) {
    return switch (direction) {
      case NORTH -> ActionDirection.NORTH;
      case SOUTH -> ActionDirection.SOUTH;
      case EAST -> ActionDirection.EAST;
      case WEST -> ActionDirection.WEST;
      default -> throw new IllegalArgumentException(
        "Water landing direction must be horizontal"
      );
    };
  }

  private record FreeColumnSubscription() implements MinecraftGraph.MovementSubscription<WaterLandingMovement> {
    private static final FreeColumnSubscription INSTANCE =
      new FreeColumnSubscription();

    @Override
    public MinecraftGraph.SubscriptionSingleResult processBlock(
      MinecraftGraph graph,
      SFVec3i key,
      WaterLandingMovement movement,
      BlockState blockState,
      SFVec3i absoluteKey
    ) {
      return SFBlockHelpers.isBlockFree(blockState)
        ? MinecraftGraph.SubscriptionSingleResult.CONTINUE
        : MinecraftGraph.SubscriptionSingleResult.IMPOSSIBLE;
    }
  }

  private record WaterSubscription() implements MinecraftGraph.MovementSubscription<WaterLandingMovement> {
    private static final WaterSubscription INSTANCE = new WaterSubscription();

    @Override
    public MinecraftGraph.SubscriptionSingleResult processBlock(
      MinecraftGraph graph,
      SFVec3i key,
      WaterLandingMovement movement,
      BlockState blockState,
      SFVec3i absoluteKey
    ) {
      return SFBlockHelpers.isSwimmableWaterBlock(blockState)
        ? MinecraftGraph.SubscriptionSingleResult.CONTINUE
        : MinecraftGraph.SubscriptionSingleResult.IMPOSSIBLE;
    }
  }

  private record SafeFloorSubscription() implements MinecraftGraph.MovementSubscription<WaterLandingMovement> {
    private static final SafeFloorSubscription INSTANCE =
      new SafeFloorSubscription();

    @Override
    public MinecraftGraph.SubscriptionSingleResult processBlock(
      MinecraftGraph graph,
      SFVec3i key,
      WaterLandingMovement movement,
      BlockState blockState,
      SFVec3i absoluteKey
    ) {
      return SFBlockHelpers.isWalkableFloorBlock(blockState)
        ? MinecraftGraph.SubscriptionSingleResult.CONTINUE
        : MinecraftGraph.SubscriptionSingleResult.IMPOSSIBLE;
    }
  }
}
