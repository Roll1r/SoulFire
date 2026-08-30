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
package com.soulfiremc.server.pathfinding.graph.constraint;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PathConstraintImplTest {
  @Test
  void ignoresPassiveNeutralMobsButAvoidsThemWhenAngry() {
    assertFalse(PathConstraintImpl.shouldAvoidEntity(false, true, false));
    assertTrue(PathConstraintImpl.shouldAvoidEntity(false, true, true));
  }

  @Test
  void continuesToAvoidOrdinaryHostileMobs() {
    assertTrue(PathConstraintImpl.shouldAvoidEntity(false, false, false));
  }

  @Test
  void avoidsNormallyFriendlyMobsOnlyWhileTheyAreAggressive() {
    assertFalse(PathConstraintImpl.shouldAvoidEntity(true, false, false));
    assertTrue(PathConstraintImpl.shouldAvoidEntity(true, false, true));
  }
}
