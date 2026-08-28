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
package com.soulfiremc.server.pathfinding;

import com.soulfiremc.server.pathfinding.graph.ProjectedInventory;

/// The canonical state used by route search.
public record NodeState(
  SFVec3i blockPosition,
  SupportSurface supportSurface,
  SupportOrigin supportOrigin,
  MovementMode movementMode,
  ResourceState resources
) {
  public NodeState(SFVec3i blockPosition, int usableBlockItems) {
    this(
      blockPosition,
      SupportSurface.FLOOR,
      SupportOrigin.WORLD,
      MovementMode.GROUND,
      ResourceState.withUsableBlockItems(usableBlockItems)
    );
  }

  public static NodeState forInfo(SFVec3i blockPosition, ProjectedInventory inventory) {
    return new NodeState(
      blockPosition,
      SupportSurface.FLOOR,
      SupportOrigin.WORLD,
      MovementMode.GROUND,
      new ResourceState(inventory.usableBlockItems())
    );
  }

  public int usableBlockItems() {
    return resources.usableBlockItems();
  }

  public NodeState withPosition(
    SFVec3i position,
    SupportSurface surface,
    SupportOrigin origin,
    MovementMode mode,
    ResourceState newResources
  ) {
    return new NodeState(
      position,
      surface,
      origin,
      mode,
      newResources
    );
  }
}
