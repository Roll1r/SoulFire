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

/// Indicates that pathfinding cannot make progress toward a goal.
public final class UnreachableGoalException extends IllegalStateException {
  private final String code;

  private UnreachableGoalException(String code, String message) {
    super(message);
    this.code = code;
  }

  public static UnreachableGoalException noRoute() {
    return new UnreachableGoalException(
      "path_no_route",
      "No route found to the goal!"
    );
  }

  public static UnreachableGoalException searchLimit(int expandedStates) {
    return new UnreachableGoalException(
      "path_search_limit",
      "Pathfinding reached its search limit after "
        + expandedStates
        + " expanded states"
    );
  }

  public static UnreachableGoalException qualityBound(
    double certifiedBound,
    double requestedBound
  ) {
    return new UnreachableGoalException(
      "path_quality_bound_not_met",
      "Pathfinding found a route with certified bound "
        + certifiedBound
        + " but the request requires "
        + requestedBound
    );
  }

  public static UnreachableGoalException stalled(int partialRouteCount) {
    return new UnreachableGoalException(
      "path_stalled",
      "Pathfinding made no progress across "
        + partialRouteCount
        + " consecutive partial routes"
    );
  }

  public static UnreachableGoalException stalledAction(
    int actionCount,
    String action
  ) {
    return new UnreachableGoalException(
      "path_action_stalled",
      "Pathfinding stalled on "
        + action
        + " across "
        + actionCount
        + " consecutive attempts"
    );
  }

  public static UnreachableGoalException worldDataTimeout(
    long snapshotRevision,
    int unavailableChunks
  ) {
    return new UnreachableGoalException(
      "path_world_data_timeout",
      "Timed out waiting for "
        + unavailableChunks
        + " navigation chunks after world revision "
        + snapshotRevision
    );
  }

  public String code() {
    return code;
  }
}
