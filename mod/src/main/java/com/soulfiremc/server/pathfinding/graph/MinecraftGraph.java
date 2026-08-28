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

import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.SupportOrigin;
import com.soulfiremc.server.pathfinding.graph.actions.*;
import com.soulfiremc.server.pathfinding.graph.constraint.PathConstraint;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.extern.slf4j.Slf4j;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/// Minecraft world, but observed as a graph of actions between blocks.
/// A node would be a block and the edges would be the actions that can be performed on that block.
/// An action MUST lead to another block in the "graph".
/// If you know graph theory this may sound familiar.
/// All checks are predefined "subscriptions" of a block relative to the foot position of a bot.
/// This way we can most efficiently calculate the actions that can be performed on a blocks by immediately running multiple actions per block.
/// Traditionally an action would dynamically check if it is possible and a block would be considered multiple times across multiple actions.
/// However, with subscriptions multiple actions can "opt-in" to a block and perform their checks on a block a single time.
///
/// @param blockAccessor  The block accessor
/// @param inventory      The inventory of the bot
/// @param pathConstraint The path constraint
@Slf4j
public record MinecraftGraph(BlockGetter blockAccessor,
                             ProjectedInventory inventory,
                             PathConstraint pathConstraint,
                             NavigationSnapshot snapshot,
                             GraphTransitionCache transitionCache) {
  public static final int ACTIONS_SIZE;
  private static final GraphAction[] ACTIONS_TEMPLATE;
  private static final SFVec3i[] SUBSCRIPTION_KEYS;
  private static final WrappedActionSubscription[][] SUBSCRIPTION_VALUES;

  public MinecraftGraph(
    BlockGetter blockAccessor,
    ProjectedInventory inventory,
    PathConstraint pathConstraint
  ) {
    this(
      blockAccessor,
      inventory,
      pathConstraint,
      new NavigationSnapshot(blockAccessor),
      new GraphTransitionCache()
    );
  }

  public MinecraftGraph(
    BlockGetter blockAccessor,
    ProjectedInventory inventory,
    PathConstraint pathConstraint,
    long worldRevision
  ) {
    this(
      blockAccessor,
      inventory,
      pathConstraint,
      new NavigationSnapshot(blockAccessor, worldRevision),
      new GraphTransitionCache()
    );
  }

  public MinecraftGraph(
    BlockGetter blockAccessor,
    ProjectedInventory inventory,
    PathConstraint pathConstraint,
    NavigationSnapshot snapshot
  ) {
    this(
      blockAccessor,
      inventory,
      pathConstraint,
      snapshot,
      new GraphTransitionCache()
    );
  }

  static {
    var blockSubscribers = new Object2ObjectOpenHashMap<SFVec3i, List<WrappedActionSubscription>>();
    var actions = new ArrayList<GraphAction>();
    var currentSubscriptions = new AtomicInteger(0);
    SubscriptionConsumer blockSubscribersConsumer = (key, value) -> {
      currentSubscriptions.incrementAndGet();
      blockSubscribers.computeIfAbsent(key, _ -> new ArrayList<>()).add(new WrappedActionSubscription(actions.size(), value));
    };
    Consumer<GraphAction> actionAdder = action -> {
      actions.add(action);
      action.subscriptionCounter(currentSubscriptions.getAndSet(0));
    };

    SimpleMovement.registerMovements(actionAdder, blockSubscribersConsumer);
    ClimbMovement.registerClimbMovements(actionAdder, blockSubscribersConsumer);
    WaterLandingMovement.registerWaterLandingMovements(
      actionAdder,
      blockSubscribersConsumer
    );
    ParkourMovement.registerParkourMovements(actionAdder, blockSubscribersConsumer);
    DownMovement.registerDownMovements(actionAdder, blockSubscribersConsumer);
    UpMovement.registerUpMovements(actionAdder, blockSubscribersConsumer);

    ACTIONS_TEMPLATE = actions.toArray(new GraphAction[0]);
    ACTIONS_SIZE = ACTIONS_TEMPLATE.length;
    SUBSCRIPTION_KEYS = new SFVec3i[blockSubscribers.size()];
    SUBSCRIPTION_VALUES = new WrappedActionSubscription[blockSubscribers.size()][];

    var entrySetDescending =
      blockSubscribers.object2ObjectEntrySet().stream()
        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
        .toList();
    for (var i = 0; i < entrySetDescending.size(); i++) {
      var entry = entrySetDescending.get(i);
      SUBSCRIPTION_KEYS[i] = entry.getKey();
      SUBSCRIPTION_VALUES[i] = entry.getValue().toArray(new WrappedActionSubscription[0]);
    }
  }

  public GraphExpansion insertActions(
    com.soulfiremc.server.pathfinding.NodeState node,
    Consumer<GraphInstructions> callback
  ) {
    log.debug("Inserting actions for node: {}", node.blockPosition());
    var cached = transitionCache.get(
      node.blockPosition(),
      node.supportOrigin()
    );
    if (cached != null) {
      cached.instructions().forEach(callback);
      return new GraphExpansion(cached.unavailableChunks());
    }

    var instructions = new ArrayList<GraphInstructions>();
    snapshot.beginBoundaryCapture();
    Set<NavigationChunk> unavailableChunks;
    try {
      calculateActions(
        node,
        generateTemplateActions(node.supportOrigin()),
        instructions::add
      );
    } finally {
      unavailableChunks = snapshot.endBoundaryCapture();
    }
    var entry = new GraphTransitionCache.Entry(
      instructions,
      unavailableChunks
    );
    transitionCache.put(node.blockPosition(), node.supportOrigin(), entry);
    entry.instructions().forEach(callback);
    return new GraphExpansion(entry.unavailableChunks());
  }

  private GraphAction[] generateTemplateActions(SupportOrigin supportOrigin) {
    var actions = new GraphAction[ACTIONS_TEMPLATE.length];
    for (var i = 0; i < ACTIONS_TEMPLATE.length; i++) {
      actions[i] = ACTIONS_TEMPLATE[i].copy();
      if (actions[i] instanceof SimpleMovement movement) {
        movement.currentFloorProjected(supportOrigin == SupportOrigin.PLACED);
      }
    }

    return actions;
  }

  private boolean calculateActions(
    com.soulfiremc.server.pathfinding.NodeState node,
    GraphAction[] actions,
    Consumer<GraphInstructions> callback) {
    var reachedLevelBoundary = false;
    for (var i = 0; i < SUBSCRIPTION_KEYS.length; i++) {
      reachedLevelBoundary |= processSubscription(
        node,
        actions,
        callback,
        i
      );
    }
    return reachedLevelBoundary;
  }

  private boolean processSubscription(
    com.soulfiremc.server.pathfinding.NodeState node,
    GraphAction[] actions,
    Consumer<GraphInstructions> callback,
    int i) {
    var key = SUBSCRIPTION_KEYS[i];
    var value = SUBSCRIPTION_VALUES[i];

    BlockState blockState = null;
    SFVec3i absolutePositionBlock = null;

    // We cache only this, but not solid because solid will only occur a single time
    for (var subscriber : value) {
      var action = actions[subscriber.actionIndex];
      if (action == null) {
        continue;
      }

      if (blockState == null) {
        // Lazy calculation to avoid unnecessary calls
        absolutePositionBlock = node.blockPosition().add(key);
        blockState = snapshot.blockState(absolutePositionBlock);

        if (pathConstraint.isOutOfLevel(blockState, absolutePositionBlock)) {
          snapshot.markUnavailable(absolutePositionBlock);
          for (var unavailableSubscriber : value) {
            actions[unavailableSubscriber.actionIndex] = null;
          }
          return true;
        }
      }

      var result = subscriber.subscription.processBlockUnsafe(this, key, action, blockState, absolutePositionBlock);
      if (result == MinecraftGraph.SubscriptionSingleResult.CONTINUE) {
        if (!action.decrementAndIsDone()) {
          continue;
        }

        for (var instruction : action.getInstructions(this, node.blockPosition())) {
          var modified = pathConstraint.modifyAsNeeded(instruction);
          if (pathConstraint.allowsInstruction(modified)) {
            callback.accept(modified);
          }
        }
      } else if (result == MinecraftGraph.SubscriptionSingleResult.IMPOSSIBLE) {
        actions[subscriber.actionIndex] = null;
      }
    }
    return false;
  }

  public enum SubscriptionSingleResult {
    CONTINUE,
    IMPOSSIBLE
  }

  public record GraphExpansion(Set<NavigationChunk> unavailableChunks) {
    public GraphExpansion {
      unavailableChunks = Set.copyOf(unavailableChunks);
    }

    public boolean reachedLevelBoundary() {
      return !unavailableChunks.isEmpty();
    }
  }

  public interface MovementSubscription<M extends GraphAction> {
    SubscriptionSingleResult processBlock(
      MinecraftGraph graph,
      SFVec3i key,
      M action,
      BlockState blockState,
      SFVec3i absoluteKey);

    @SuppressWarnings("unchecked")
    default SubscriptionSingleResult processBlockUnsafe(
      MinecraftGraph graph,
      SFVec3i key,
      GraphAction action,
      BlockState blockState,
      SFVec3i absolutePositionBlock) {
      return processBlock(graph, key, (M) action, blockState, absolutePositionBlock);
    }
  }

  private record WrappedActionSubscription(int actionIndex, MovementSubscription<?> subscription) {}
}
