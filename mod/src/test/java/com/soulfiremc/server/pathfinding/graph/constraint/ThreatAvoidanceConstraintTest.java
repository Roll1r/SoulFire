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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ThreatAvoidanceConstraintTest {
  @Test
  void preventsEnteringAThreatExclusionRadius() {
    var constraint = constraint(
      SFVec3i.ZERO,
      new ThreatAvoidanceConstraint.Threat(
        SFVec3i.from(5, 0, 0),
        4,
        10,
        100
      )
    );

    assertFalse(constraint.allowsInstruction(
      instruction(SFVec3i.from(2, 0, 0)),
      null
    ));
    assertTrue(constraint.allowsInstruction(
      instruction(SFVec3i.from(1, 0, 0)),
      null
    ));
  }

  @Test
  void letsAnEndangeredObserverMoveOutButNotCloser() {
    var constraint = constraint(
      SFVec3i.from(3, 0, 0),
      new ThreatAvoidanceConstraint.Threat(
        SFVec3i.from(5, 0, 0),
        4,
        10,
        100
      )
    );

    assertTrue(constraint.allowsInstruction(
      instruction(SFVec3i.from(2, 0, 0)),
      null
    ));
    assertFalse(constraint.allowsInstruction(
      instruction(SFVec3i.from(4, 0, 0)),
      null
    ));
  }

  @Test
  void letsAnObserverEscapeOverlappingExclusions() {
    var constraint = constraint(
      SFVec3i.ZERO,
      List.of(
        new ThreatAvoidanceConstraint.Threat(
          SFVec3i.from(-2, 0, 0),
          4,
          10,
          100
        ),
        new ThreatAvoidanceConstraint.Threat(
          SFVec3i.from(2, 0, 0),
          4,
          10,
          100
        )
      )
    );

    assertTrue(constraint.allowsInstruction(
      instruction(SFVec3i.from(1, 0, 0)),
      null
    ));
  }

  @Test
  void addsNonNegativeExposureToTransitionCost() {
    var constraint = constraint(
      SFVec3i.from(10, 0, 0),
      new ThreatAvoidanceConstraint.Threat(
        SFVec3i.ZERO,
        2,
        10,
        100
      )
    );

    var modified = constraint.modifyAsNeeded(
      instruction(SFVec3i.from(5, 0, 0))
    );

    assertEquals(26, modified.actionCost());
    assertEquals(1, constraint.modifyAsNeeded(
      instruction(SFVec3i.from(10, 0, 0))
    ).actionCost());
  }

  private static ThreatAvoidanceConstraint constraint(
    SFVec3i observerPosition,
    ThreatAvoidanceConstraint.Threat threat
  ) {
    return constraint(observerPosition, List.of(threat));
  }

  private static ThreatAvoidanceConstraint constraint(
    SFVec3i observerPosition,
    List<ThreatAvoidanceConstraint.Threat> threats
  ) {
    return new ThreatAvoidanceConstraint(
      TestPathConstraint.INSTANCE,
      new ThreatAvoidanceConstraint.Snapshot(
        observerPosition,
        threats
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
