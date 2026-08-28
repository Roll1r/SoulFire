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
import com.soulfiremc.server.pathfinding.cost.Costs;
import com.soulfiremc.server.pathfinding.execution.ClimbAction;
import com.soulfiremc.server.pathfinding.graph.GraphInstructions;
import com.soulfiremc.server.pathfinding.graph.MinecraftGraph;
import com.soulfiremc.server.pathfinding.graph.actions.movement.ActionDirection;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Consumer;

/// Moves one block vertically through a continuous climbable column.
public final class ClimbMovement extends GraphAction implements Cloneable {
  private final boolean ascending;
  private final SFVec3i targetFeetBlock;

  private ClimbMovement(
    boolean ascending,
    SubscriptionConsumer blockSubscribers
  ) {
    super(ascending ? ActionDirection.UP : ActionDirection.DOWN);
    this.ascending = ascending;
    this.targetFeetBlock = SFVec3i.from(0, ascending ? 1 : -1, 0);
    blockSubscribers.subscribe(
      SFVec3i.ZERO,
      ClimbableSubscription.INSTANCE
    );
    blockSubscribers.subscribe(
      targetFeetBlock,
      ClimbableSubscription.INSTANCE
    );
  }

  public static void registerClimbMovements(
    Consumer<GraphAction> callback,
    SubscriptionConsumer blockSubscribers
  ) {
    callback.accept(new ClimbMovement(true, blockSubscribers));
    callback.accept(new ClimbMovement(false, blockSubscribers));
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
      Costs.CLIMB,
      List.of(new ClimbAction(target, ascending))
    ));
  }

  @Override
  public ClimbMovement copy() {
    return clone();
  }

  @Override
  public ClimbMovement clone() {
    try {
      return (ClimbMovement) super.clone();
    } catch (CloneNotSupportedException _) {
      throw new InternalError();
    }
  }

  private record ClimbableSubscription() implements MinecraftGraph.MovementSubscription<ClimbMovement> {
    private static final ClimbableSubscription INSTANCE =
      new ClimbableSubscription();

    @Override
    public MinecraftGraph.SubscriptionSingleResult processBlock(
      MinecraftGraph graph,
      SFVec3i key,
      ClimbMovement movement,
      BlockState blockState,
      SFVec3i absoluteKey
    ) {
      return blockState.is(BlockTags.CLIMBABLE)
        || blockState.is(Blocks.LADDER)
        || blockState.is(Blocks.SCAFFOLDING)
        ? MinecraftGraph.SubscriptionSingleResult.CONTINUE
        : MinecraftGraph.SubscriptionSingleResult.IMPOSSIBLE;
    }
  }
}
