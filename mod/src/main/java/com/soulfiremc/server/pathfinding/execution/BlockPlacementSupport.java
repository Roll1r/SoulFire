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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/// Resolves a server-valid block interaction from live player, inventory, and
/// world state. Search actions describe the block that must exist. This helper
/// deliberately resolves the support face again at execution time because a
/// visible alternate face is safer than replaying a stale planned click.
public final class BlockPlacementSupport {
  private static final double INTERACTION_REACH_SQUARED = 36.0D;
  private static final double FACE_RAY_OVERSHOOT = 0.1D;
  private static final double PLAYER_CLEARANCE_MARGIN = 0.08D;
  private static final double PREDICTED_MOTION_TICKS = 2.0D;
  private static final double MAX_HORIZONTAL_PLACEMENT_DRIFT = 0.025D;
  private static final double MAX_VIEW_REPOSITION_DISTANCE = 3.0D;
  private static final double VIEW_PATH_SAMPLE_DISTANCE = 0.2D;
  private static final int VIEW_ANGLE_SAMPLES = 16;
  private static final double[] VIEW_RADII = {
    0.0D,
    0.2D,
    0.4D,
    0.6D,
    0.8D,
    1.0D,
    1.25D,
    1.5D,
    1.75D,
    2.0D,
    2.5D,
    3.0D
  };

  private BlockPlacementSupport() {
  }

  public static Evaluation evaluate(
    BotConnection connection,
    InteractionHand hand,
    BlockPos target,
    BlockPos preferredAgainst,
    Direction preferredFace
  ) {
    var minecraft = connection.minecraft();
    var player = minecraft.player;
    var level = minecraft.level;
    if (player == null || level == null) {
      return Evaluation.notReady(
        Readiness.GAME_STATE_UNAVAILABLE,
        "Bot player or level is unavailable"
      );
    }

    var held = player.getItemInHand(hand);
    if (!(held.getItem() instanceof BlockItem blockItem)) {
      return Evaluation.notReady(
        Readiness.HELD_ITEM_NOT_BLOCK,
        held.isEmpty()
          ? "The selected hand is empty"
          : "The selected item is not a block item"
      );
    }

    var current = level.getBlockState(target);
    if (current.getBlock() == blockItem.getBlock()) {
      return Evaluation.notReady(
        Readiness.ALREADY_PLACED,
        "The requested block already occupies the target"
      );
    }

    var orderedFaces = orderedFaces(
      target,
      preferredAgainst,
      preferredFace,
      player.getEyePosition()
    );
    var sawSupport = false;
    var sawReachableSupport = false;
    var sawVisibleSupport = false;
    var sawReplaceableTarget = false;
    var lastInvalidPlacement = "No placement state was available";
    for (var face : orderedFaces) {
      var against = target.relative(face.getOpposite());
      var againstState = level.getBlockState(against);
      if (againstState.canBeReplaced()
        || againstState.getCollisionShape(level, against).isEmpty()) {
        continue;
      }
      sawSupport = true;

      var hitPosition = faceCenter(against, face);
      if (player.getEyePosition().distanceToSqr(hitPosition)
        > INTERACTION_REACH_SQUARED) {
        continue;
      }
      sawReachableSupport = true;

      var ray = level.clip(new ClipContext(
        player.getEyePosition(),
        overshootFace(player.getEyePosition(), hitPosition),
        ClipContext.Block.OUTLINE,
        ClipContext.Fluid.NONE,
        player
      ));
      if (!ray.getBlockPos().equals(against)
        || !against.relative(ray.getDirection()).equals(target)) {
        continue;
      }
      sawVisibleSupport = true;

      var hit = new BlockHitResult(
        hitPosition,
        face,
        against,
        false
      );
      var context = new BlockPlaceContext(player, hand, held, hit);
      if (!context.canPlace()) {
        continue;
      }
      sawReplaceableTarget = true;

      var adjustedContext = blockItem.updatePlacementContext(context);
      if (adjustedContext == null
        || !adjustedContext.getClickedPos().equals(target)) {
        lastInvalidPlacement =
          "The held item redirected placement away from the target";
        continue;
      }
      var expectedState = blockItem.getBlock()
        .getStateForPlacement(adjustedContext);
      if (expectedState == null) {
        lastInvalidPlacement =
          "The held block has no valid state at the target";
        continue;
      }
      if (!expectedState.canSurvive(level, target)) {
        lastInvalidPlacement =
          "The held block cannot survive at the target";
        continue;
      }

      var candidate = new Candidate(
        target,
        against,
        face,
        hitPosition,
        expectedState
      );
      if (requiresPlayerClearance(
        player.getBoundingBox(),
        player.getDeltaMovement(),
        target
      )) {
        return new Evaluation(
          Readiness.PLAYER_INTERSECTION,
          candidate,
          "The player or their residual motion intersects the target"
        );
      }
      if (!level.isUnobstructed(
        expectedState,
        target,
        net.minecraft.world.phys.shapes.CollisionContext
          .placementContext(player)
      )) {
        return new Evaluation(
          Readiness.ENTITY_OBSTRUCTION,
          candidate,
          "Another entity obstructs the target"
        );
      }
      if (!isHorizontallyStable(player.getDeltaMovement())) {
        return new Evaluation(
          Readiness.PLAYER_MOVING,
          candidate,
          "The player still has horizontal placement momentum"
        );
      }
      return new Evaluation(
        Readiness.READY,
        candidate,
        "Placement target is ready"
      );
    }

    if (!sawSupport) {
      return Evaluation.notReady(
        Readiness.NO_SUPPORT,
        "No non-replaceable neighboring block can support the target"
      );
    }
    if (!sawReachableSupport) {
      return Evaluation.notReady(
        Readiness.OUT_OF_REACH,
        "Every placement face is outside interaction reach"
      );
    }
    if (!sawVisibleSupport) {
      return Evaluation.notReady(
        Readiness.FACE_OCCLUDED,
        "No placement face is visible from the player's eye position"
      );
    }
    if (!sawReplaceableTarget) {
      return Evaluation.notReady(
        Readiness.TARGET_NOT_REPLACEABLE,
        "The target is not replaceable for the held block"
      );
    }
    return Evaluation.notReady(
      Readiness.INVALID_PLACEMENT_STATE,
      lastInvalidPlacement
    );
  }

  public static void moveToPlayerClearance(
    BotConnection connection,
    BlockPos target
  ) {
    var player = connection.minecraft().player;
    if (player == null) {
      return;
    }
    var clearance = nearestClearancePosition(
      player.getBoundingBox(),
      player.position(),
      target
    );
    connection.rotationControl().lookHorizontallyAt(clearance);
    var movement = MovementAction.movementInputFor(
      player.position(),
      player.getYRot(),
      clearance
    );
    var controls = connection.controlState();
    controls.up(movement.forward());
    controls.down(movement.backward());
    controls.left(movement.left());
    controls.right(movement.right());
    controls.shift(true);
  }

  /// Moves toward the nearest locally reachable position that exposes one
  /// of the target's placement faces. This is the execution counterpart to a
  /// bridge back-place: the route can validly end on the support block while
  /// the player's centered eye ray still hits its top instead of its side.
  /// Sneaking lets the player approach that edge without walking into the
  /// unsupported target cell.
  public static boolean moveTowardPlacementView(
    BotConnection connection,
    BlockPos target,
    BlockPos preferredAgainst,
    Direction preferredFace
  ) {
    var minecraft = connection.minecraft();
    var player = minecraft.player;
    var level = minecraft.level;
    if (player == null || level == null) {
      return false;
    }

    var playerPosition = player.position();
    var eyeOffset = player.getEyePosition().subtract(playerPosition);
    var view = placementViewCandidates(playerPosition, target).stream()
      .filter(candidate -> candidate.distanceToSqr(playerPosition)
        <= MAX_VIEW_REPOSITION_DISTANCE * MAX_VIEW_REPOSITION_DISTANCE)
      .filter(candidate -> {
        var movement = candidate.subtract(playerPosition);
        var candidateBounds = player.getBoundingBox().move(movement);
        return !requiresPlayerClearance(
          candidateBounds,
          Vec3.ZERO,
          target
        ) && hasClearHorizontalPath(
          connection,
          player.getBoundingBox(),
          movement
        ) && hasVisiblePlacementFace(
          connection,
          candidate.add(eyeOffset),
          target,
          preferredAgainst,
          preferredFace
        );
      })
      .findFirst();
    if (view.isEmpty()) {
      return false;
    }

    var targetView = view.orElseThrow();
    connection.rotationControl().lookHorizontallyAt(targetView);
    var movement = MovementAction.movementInputFor(
      playerPosition,
      player.getYRot(),
      targetView
    );
    var controls = connection.controlState();
    controls.up(movement.forward());
    controls.down(movement.backward());
    controls.left(movement.left());
    controls.right(movement.right());
    controls.shift(true);
    return true;
  }

  static List<Vec3> placementViewCandidates(
    Vec3 playerPosition,
    BlockPos target
  ) {
    var targetCenter = Vec3.atCenterOf(target);
    var horizontalCenter = new Vec3(
      targetCenter.x,
      playerPosition.y,
      targetCenter.z
    );
    var candidates = new ArrayList<Vec3>();
    candidates.add(playerPosition);
    for (var radius : VIEW_RADII) {
      if (radius == 0.0D) {
        candidates.add(horizontalCenter);
        continue;
      }
      for (var index = 0; index < VIEW_ANGLE_SAMPLES; index++) {
        var angle = 2.0D * Math.PI * index / VIEW_ANGLE_SAMPLES;
        candidates.add(horizontalCenter.add(
          Math.cos(angle) * radius,
          0,
          Math.sin(angle) * radius
        ));
      }
    }
    candidates.sort(
      Comparator.comparingDouble(candidate ->
        candidate.distanceToSqr(playerPosition))
    );
    return List.copyOf(candidates);
  }

  private static boolean hasClearHorizontalPath(
    BotConnection connection,
    AABB playerBounds,
    Vec3 movement
  ) {
    var horizontalDistance = Math.hypot(movement.x, movement.z);
    var samples = Math.max(
      1,
      (int) Math.ceil(horizontalDistance / VIEW_PATH_SAMPLE_DISTANCE)
    );
    var level = connection.minecraft().level;
    var player = connection.minecraft().player;
    for (var index = 1; index <= samples; index++) {
      var progress = (double) index / samples;
      var bounds = playerBounds.move(
        movement.x * progress,
        0,
        movement.z * progress
      );
      if (level.getBlockCollisions(player, bounds).iterator().hasNext()) {
        return false;
      }
    }
    return true;
  }

  private static boolean hasVisiblePlacementFace(
    BotConnection connection,
    Vec3 eyePosition,
    BlockPos target,
    BlockPos preferredAgainst,
    Direction preferredFace
  ) {
    var level = connection.minecraft().level;
    var player = connection.minecraft().player;
    for (var face : orderedFaces(
      target,
      preferredAgainst,
      preferredFace,
      eyePosition
    )) {
      var against = target.relative(face.getOpposite());
      var againstState = level.getBlockState(against);
      if (againstState.canBeReplaced()
        || againstState.getCollisionShape(level, against).isEmpty()) {
        continue;
      }
      var hitPosition = faceCenter(against, face);
      if (eyePosition.distanceToSqr(hitPosition)
        > INTERACTION_REACH_SQUARED) {
        continue;
      }
      var ray = level.clip(new ClipContext(
        eyePosition,
        overshootFace(eyePosition, hitPosition),
        ClipContext.Block.OUTLINE,
        ClipContext.Fluid.NONE,
        player
      ));
      if (ray.getBlockPos().equals(against)
        && against.relative(ray.getDirection()).equals(target)) {
        return true;
      }
    }
    return false;
  }

  static boolean requiresPlayerClearance(
    AABB playerBounds,
    Vec3 motion,
    BlockPos target
  ) {
    var predictedBounds = playerBounds
      .expandTowards(
        motion.x * PREDICTED_MOTION_TICKS,
        0,
        motion.z * PREDICTED_MOTION_TICKS
      )
      .inflate(PLAYER_CLEARANCE_MARGIN, 0, PLAYER_CLEARANCE_MARGIN);
    return predictedBounds.intersects(new AABB(target));
  }

  static Vec3 nearestClearancePosition(
    AABB playerBounds,
    Vec3 playerPosition,
    BlockPos target
  ) {
    var halfWidth = Math.max(
      playerBounds.getXsize(),
      playerBounds.getZsize()
    ) / 2.0D;
    var offset = halfWidth + PLAYER_CLEARANCE_MARGIN;
    var candidates = List.of(
      new Vec3(target.getX() - offset, playerPosition.y, playerPosition.z),
      new Vec3(target.getX() + 1 + offset, playerPosition.y, playerPosition.z),
      new Vec3(playerPosition.x, playerPosition.y, target.getZ() - offset),
      new Vec3(playerPosition.x, playerPosition.y, target.getZ() + 1 + offset)
    );
    return candidates.stream()
      .min(Comparator.comparingDouble(candidate ->
        candidate.distanceToSqr(playerPosition)))
      .orElseThrow();
  }

  static boolean isHorizontallyStable(Vec3 motion) {
    return Math.hypot(motion.x, motion.z)
      < MAX_HORIZONTAL_PLACEMENT_DRIFT;
  }

  static List<Direction> orderedFaces(
    BlockPos target,
    BlockPos preferredAgainst,
    Direction preferredFace,
    Vec3 eyePosition
  ) {
    var result = new ArrayList<Direction>();
    if (preferredAgainst.relative(preferredFace).equals(target)) {
      result.add(preferredFace);
    }
    List.of(Direction.values()).stream()
      .filter(face -> !result.contains(face))
      .sorted(Comparator.comparingDouble(face ->
        eyePosition.distanceToSqr(faceCenter(
          target.relative(face.getOpposite()),
          face
        ))))
      .forEach(result::add);
    return List.copyOf(result);
  }

  private static Vec3 faceCenter(BlockPos against, Direction face) {
    return Vec3.atCenterOf(against).add(
      face.getStepX() * 0.5D,
      face.getStepY() * 0.5D,
      face.getStepZ() * 0.5D
    );
  }

  private static Vec3 overshootFace(Vec3 eyePosition, Vec3 faceCenter) {
    var delta = faceCenter.subtract(eyePosition);
    if (delta.lengthSqr() < 1.0E-12D) {
      return faceCenter;
    }
    return faceCenter.add(delta.normalize().scale(FACE_RAY_OVERSHOOT));
  }

  public enum Readiness {
    READY,
    ALREADY_PLACED,
    HELD_ITEM_NOT_BLOCK,
    TARGET_NOT_REPLACEABLE,
    NO_SUPPORT,
    OUT_OF_REACH,
    FACE_OCCLUDED,
    PLAYER_INTERSECTION,
    PLAYER_MOVING,
    ENTITY_OBSTRUCTION,
    INVALID_PLACEMENT_STATE,
    GAME_STATE_UNAVAILABLE
  }

  public record Candidate(
    BlockPos target,
    BlockPos against,
    Direction face,
    Vec3 hitPosition,
    BlockState expectedState
  ) {
    public BlockHitResult hitResult() {
      return new BlockHitResult(
        hitPosition,
        face,
        against,
        false
      );
    }
  }

  public record Evaluation(
    Readiness readiness,
    Candidate candidate,
    String detail
  ) {
    private static Evaluation notReady(
      Readiness readiness,
      String detail
    ) {
      return new Evaluation(readiness, null, detail);
    }

    public boolean ready() {
      return readiness == Readiness.READY;
    }
  }
}
