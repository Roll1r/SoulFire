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

import com.soulfiremc.server.pathfinding.SFVec3i;
import com.soulfiremc.server.pathfinding.graph.GraphInstructions;
import com.soulfiremc.test.utils.TestPathConstraint;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProjectileAvoidanceConstraintTest {
  private static final ProjectileAvoidanceConstraint.Trajectory TRAJECTORY =
    new ProjectileAvoidanceConstraint.Trajectory(
      7,
      List.of(new ProjectileAvoidanceConstraint.Segment(
        new Vec3(-5, 0.9, 0.5),
        new Vec3(5, 0.9, 0.5)
      ))
    );

  @Test
  void preventsEnteringTheFutureProjectileCorridor() {
    var constraint = constraint(new Vec3(0.5, 0.9, 5.5));

    assertFalse(constraint.allowsInstruction(
      instruction(SFVec3i.ZERO),
      null
    ));
    assertTrue(constraint.allowsInstruction(
      instruction(SFVec3i.from(0, 0, 5)),
      null
    ));
  }

  @Test
  void letsAnEndangeredPlayerIncreaseTrajectoryClearance() {
    var constraint = constraint(
      ProjectileAvoidanceConstraint.playerCenter(SFVec3i.from(0, 0, 1))
    );

    assertTrue(constraint.allowsInstruction(
      instruction(SFVec3i.from(0, 0, 2)),
      null
    ));
    assertFalse(constraint.allowsInstruction(
      instruction(SFVec3i.ZERO),
      null
    ));
  }

  @Test
  void addsNonNegativeNearTrajectoryExposureCost() {
    var constraint = constraint(new Vec3(0.5, 0.9, 5.5));

    assertEquals(9, constraint.modifyAsNeeded(
      instruction(SFVec3i.from(0, 0, 3))
    ).actionCost());
    assertEquals(1, constraint.modifyAsNeeded(
      instruction(SFVec3i.from(0, 0, 5))
    ).actionCost());
  }

  private static ProjectileAvoidanceConstraint constraint(
    Vec3 observerCenter
  ) {
    return new ProjectileAvoidanceConstraint(
      TestPathConstraint.INSTANCE,
      new ProjectileAvoidanceConstraint.Snapshot(
        observerCenter,
        List.of(TRAJECTORY)
      )
    );
  }

  private static GraphInstructions instruction(SFVec3i position) {
    return new GraphInstructions(
      position,
      0,
      false,
      null,
      1,
      List.of()
    );
  }
}
