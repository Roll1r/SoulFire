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
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.Set;

/// Search-local cache for deterministic graph transitions.
public final class GraphTransitionCache {
  private final Long2ObjectOpenHashMap<Entry> worldSupport =
    new Long2ObjectOpenHashMap<>();
  private final Long2ObjectOpenHashMap<Entry> placedSupport =
    new Long2ObjectOpenHashMap<>();
  private long hits;
  private long misses;

  public @Nullable Entry get(SFVec3i position, SupportOrigin origin) {
    var entry = map(origin).get(position.asMinecraftLong());
    if (entry == null) {
      misses++;
    } else {
      hits++;
    }
    return entry;
  }

  public void put(SFVec3i position, SupportOrigin origin, Entry entry) {
    map(origin).put(position.asMinecraftLong(), entry);
  }

  private Long2ObjectOpenHashMap<Entry> map(SupportOrigin origin) {
    return origin == SupportOrigin.PLACED ? placedSupport : worldSupport;
  }

  public long hits() {
    return hits;
  }

  public long misses() {
    return misses;
  }

  public record Entry(
    List<GraphInstructions> instructions,
    Set<NavigationChunk> unavailableChunks
  ) {
    public Entry {
      instructions = List.copyOf(instructions);
      unavailableChunks = Set.copyOf(unavailableChunks);
    }
  }
}
