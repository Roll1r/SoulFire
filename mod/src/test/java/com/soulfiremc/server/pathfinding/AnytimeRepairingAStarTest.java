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

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnytimeRepairingAStarTest {
  private static final Map<String, List<Edge>> REPAIR_GRAPH = Map.of(
    "S", List.of(new Edge("A", 5), new Edge("B", 1)),
    "A", List.of(new Edge("G", 5)),
    "B", List.of(new Edge("A", 1)),
    "G", List.of()
  );
  private static final Map<String, Double> REPAIR_HEURISTIC = Map.of(
    "S", 7D,
    "A", 0D,
    "B", 2D,
    "G", 0D
  );

  @Test
  void acceptsAnAlreadySatisfiedStartWithoutExpansion() {
    var search = new AnytimeRepairingAStar<>(
      "G",
      new StringDomain(
        Map.of("G", List.of()),
        Map.of("G", 0D),
        "G",
        Set.of()
      ),
      configuration(2.5, 1, 100)
    );

    var outcome = search.search();
    var diagnostics = search.boundaryDiagnostics();

    assertEquals(AnytimeRepairingAStar.Status.FOUND, outcome.status());
    assertEquals(List.of(), outcome.path());
    assertEquals(0, outcome.cost());
    assertEquals(1, outcome.certifiedQualityBound());
    assertEquals(0, outcome.expandedStates());
    assertEquals("G", diagnostics.closestState());
    assertTrue(diagnostics.closestExpandedState().isEmpty());
    assertTrue(diagnostics.closestExpandedHeuristic().isEmpty());
  }

  @Test
  void exactSearchMatchesTheDijkstraCost() {
    var outcome = search(
      new StringDomain(REPAIR_GRAPH, REPAIR_HEURISTIC, "G", Set.of()),
      configuration(1, 1, 100)
    );

    assertEquals(AnytimeRepairingAStar.Status.FOUND, outcome.status());
    assertEquals(7, outcome.cost());
    assertEquals(1, outcome.certifiedQualityBound());
  }

  @Test
  void repairsClosedStatesThroughInconsistentSet() {
    var outcome = search(
      new StringDomain(REPAIR_GRAPH, REPAIR_HEURISTIC, "G", Set.of()),
      configuration(2.5, 1, 100)
    );

    assertEquals(7, outcome.cost());
    assertTrue(outcome.repairIterations() > 0);
    assertTrue(outcome.repairedInconsistentStates() > 0);
    assertEquals(List.of(10D, 7D), outcome.incumbentCosts());
  }

  @Test
  void mayReturnTheFirstCertifiedRouteWithoutRepair() {
    var outcome = search(
      new StringDomain(REPAIR_GRAPH, REPAIR_HEURISTIC, "G", Set.of()),
      configuration(2.5, 2.5, 100, 0)
    );

    assertEquals(AnytimeRepairingAStar.Status.FOUND, outcome.status());
    assertEquals(10, outcome.cost());
    assertEquals(2.5, outcome.certifiedQualityBound());
    assertTrue(
      outcome.cost() / 7 <= outcome.certifiedQualityBound()
    );
    assertEquals(0, outcome.repairIterations());
  }

  @Test
  void incumbentCostOnlyImprovesAcrossRepairs() {
    var outcome = search(
      new StringDomain(REPAIR_GRAPH, REPAIR_HEURISTIC, "G", Set.of()),
      configuration(2.5, 1, 100)
    );

    for (var index = 1; index < outcome.incumbentCosts().size(); index++) {
      assertTrue(
        outcome.incumbentCosts().get(index)
          < outcome.incumbentCosts().get(index - 1)
      );
    }
  }

  @Test
  void appliesExpansionBudgetAcrossTheCompleteRepairSession() {
    var outcome = search(
      new StringDomain(REPAIR_GRAPH, REPAIR_HEURISTIC, "G", Set.of()),
      configuration(2.5, 1, 2)
    );

    assertEquals(
      AnytimeRepairingAStar.Status.QUALITY_BOUND_NOT_MET,
      outcome.status()
    );
    assertEquals(2, outcome.expandedStates());
    assertEquals(10, outcome.cost());
    assertEquals(10D / 3D, outcome.certifiedQualityBound());
  }

  @Test
  void doesNotTreatEpsilonAsACertificateForAnInterruptedStage() {
    var outcome = search(
      new StringDomain(REPAIR_GRAPH, REPAIR_HEURISTIC, "G", Set.of()),
      configuration(2.5, 2.5, 2)
    );

    assertEquals(
      AnytimeRepairingAStar.Status.QUALITY_BOUND_NOT_MET,
      outcome.status()
    );
    assertEquals(10D / 3D, outcome.certifiedQualityBound());
  }

  @Test
  void doesNotTurnArbitraryProgressIntoAPartialRoute() {
    var graph = Map.of(
      "S", List.of(new Edge("A", 1)),
      "A", List.<Edge>of()
    );
    var outcome = search(
      new StringDomain(
        graph,
        Map.of("S", 2D, "A", 1D),
        "G",
        Set.of()
      ),
      configuration(1, 1, 1)
    );

    assertEquals(
      AnytimeRepairingAStar.Status.SEARCH_LIMIT,
      outcome.status()
    );
    assertTrue(outcome.path().isEmpty());
  }

  @Test
  void returnsOnlyADeclaredWorldFrontierAsAPartialRoute() {
    var graph = Map.of(
      "S", List.of(new Edge("A", 1)),
      "A", List.of(new Edge("B", 1)),
      "B", List.<Edge>of()
    );
    var outcome = search(
      new StringDomain(
        graph,
        Map.of("S", 3D, "A", 2D, "B", 1D),
        "G",
        Set.of("A")
      ),
      configuration(1, 1, 2)
    );

    assertEquals(AnytimeRepairingAStar.Status.PARTIAL, outcome.status());
    assertEquals("A", outcome.endpoint());
    assertEquals(List.of(new Edge("A", 1)), outcome.path());
  }

  @Test
  void reportsAStationaryWorldFrontierWithoutInventingProgress() {
    var outcome = search(
      new StringDomain(
        Map.of("S", List.of()),
        Map.of("S", 1D),
        "G",
        Set.of("S")
      ),
      configuration(1, 1, 100)
    );

    assertEquals(
      AnytimeRepairingAStar.Status.WORLD_DATA_PENDING,
      outcome.status()
    );
    assertEquals(AnytimeRepairingAStar.StopReason.FRONTIER, outcome.stopReason());
    assertEquals(1, outcome.expandedStates());
    assertEquals("S", outcome.endpoint());
    assertTrue(outcome.path().isEmpty());
  }

  @Test
  void doesNotWaitAtARegressiveWorldFrontier() {
    var graph = Map.of(
      "S", List.of(new Edge("A", 1)),
      "A", List.<Edge>of()
    );
    var outcome = search(
      new StringDomain(
        graph,
        Map.of("S", 1D, "A", 2D),
        "G",
        Set.of("A")
      ),
      configuration(1, 1, 100)
    );

    assertEquals(AnytimeRepairingAStar.Status.UNREACHABLE, outcome.status());
    assertTrue(outcome.path().isEmpty());
  }

  @Test
  void ignoresABlockedDirectionWhileLoadedProgressRemains() {
    var graph = Map.of(
      "S", List.of(new Edge("A", 1)),
      "A", List.of(new Edge("B", 1)),
      "B", List.of(new Edge("G", 1)),
      "G", List.<Edge>of()
    );
    var domain = new StringDomain(
      graph,
      Map.of("S", 3D, "A", 2D, "B", 1D, "G", 0D),
      "G",
      Set.of("S")
    );
    var outcome = search(
      domain,
      new AnytimeRepairingAStar.Configuration(
        1,
        1,
        0.5,
        System.nanoTime() + Duration.ofSeconds(5).toNanos(),
        Duration.ofSeconds(1).toNanos(),
        100,
        1,
        () -> false
      )
    );

    assertEquals(AnytimeRepairingAStar.Status.FOUND, outcome.status());
    assertEquals("G", outcome.endpoint());
  }

  @Test
  void frontierBudgetDoesNotInterruptIncumbentCertification() {
    var graph = Map.of(
      "S", List.of(new Edge("A", 1), new Edge("B", 2)),
      "A", List.<Edge>of(),
      "B", List.of(new Edge("G", 10), new Edge("C", 1)),
      "C", List.of(new Edge("G", 1)),
      "G", List.<Edge>of()
    );
    var domain = new StringDomain(
      graph,
      Map.of("S", 4D, "A", 1D, "B", 2D, "C", 1D, "G", 0D),
      "G",
      Set.of("A")
    );
    var outcome = search(
      domain,
      new AnytimeRepairingAStar.Configuration(
        1,
        1,
        0.5,
        System.nanoTime() + Duration.ofSeconds(5).toNanos(),
        Duration.ofSeconds(1).toNanos(),
        100,
        1,
        () -> false
      )
    );

    assertEquals(AnytimeRepairingAStar.Status.FOUND, outcome.status());
    assertEquals(4, outcome.cost());
    assertEquals(1, outcome.certifiedQualityBound());
  }

  @Test
  void retainsARequiredResourceTradeoff() {
    var start = new ResourceNode("S", 0);
    var search = new AnytimeRepairingAStar<>(
      start,
      new ResourceDomain(),
      configuration(1, 1, 100)
    );
    var outcome = search.search();

    assertEquals(AnytimeRepairingAStar.Status.FOUND, outcome.status());
    assertEquals(3, outcome.cost());
    assertEquals(2, outcome.endpoint().blocks());
  }

  private static AnytimeRepairingAStar.Outcome<String, Edge> search(
    StringDomain domain,
    AnytimeRepairingAStar.Configuration configuration
  ) {
    return new AnytimeRepairingAStar<>(
      "S",
      domain,
      configuration
    ).search();
  }

  private static AnytimeRepairingAStar.Configuration configuration(
    double initialEpsilon,
    double requiredBound,
    long maximumExpandedStates
  ) {
    return configuration(
      initialEpsilon,
      requiredBound,
      maximumExpandedStates,
      Duration.ofSeconds(1).toNanos()
    );
  }

  private static AnytimeRepairingAStar.Configuration configuration(
    double initialEpsilon,
    double requiredBound,
    long maximumExpandedStates,
    long improvementBudgetNanos
  ) {
    return new AnytimeRepairingAStar.Configuration(
      initialEpsilon,
      requiredBound,
      0.5,
      System.nanoTime() + Duration.ofSeconds(5).toNanos(),
      improvementBudgetNanos,
      maximumExpandedStates,
      100,
      () -> false
    );
  }

  private record Edge(String target, double cost) {}

  private record StringDomain(
    Map<String, List<Edge>> graph,
    Map<String, Double> heuristic,
    String goal,
    Set<String> frontiers
  ) implements AnytimeRepairingAStar.Domain<String, Edge> {
    @Override
    public double heuristic(String state) {
      return heuristic.getOrDefault(state, 0D);
    }

    @Override
    public boolean isGoal(String state, @Nullable Edge incomingEdge) {
      return state.equals(goal);
    }

    @Override
    public boolean expand(
      String state,
      Consumer<AnytimeRepairingAStar.Transition<String, Edge>> output
    ) {
      for (var edge : graph.getOrDefault(state, List.of())) {
        output.accept(new AnytimeRepairingAStar.Transition<>(
          edge.target(),
          edge.cost(),
          edge
        ));
      }
      return frontiers.contains(state);
    }

    @Override
    public Object dominanceKey(String state) {
      return state;
    }

    @Override
    public boolean dominates(String left, String right) {
      return left.equals(right);
    }
  }

  private record ResourceNode(String physicalState, int blocks) {}

  private static final class ResourceDomain implements
    AnytimeRepairingAStar.Domain<ResourceNode, ResourceNode> {
    @Override
    public double heuristic(ResourceNode state) {
      return switch (state.physicalState()) {
        case "S" -> 2;
        case "X" -> 1;
        default -> 0;
      };
    }

    @Override
    public boolean isGoal(
      ResourceNode state,
      @Nullable ResourceNode incomingEdge
    ) {
      return state.physicalState().equals("G");
    }

    @Override
    public boolean expand(
      ResourceNode state,
      Consumer<
        AnytimeRepairingAStar.Transition<ResourceNode, ResourceNode>
      > output
    ) {
      switch (state.physicalState()) {
        case "S" -> {
          add(output, new ResourceNode("X", 0), 1);
          add(output, new ResourceNode("X", 2), 2);
        }
        case "X" -> {
          if (state.blocks() >= 2) {
            add(output, new ResourceNode("G", state.blocks()), 1);
          }
        }
        default -> {
        }
      }
      return false;
    }

    @Override
    public Object dominanceKey(ResourceNode state) {
      return state.physicalState();
    }

    @Override
    public boolean dominates(ResourceNode left, ResourceNode right) {
      return left.blocks() >= right.blocks();
    }

    private static void add(
      Consumer<
        AnytimeRepairingAStar.Transition<ResourceNode, ResourceNode>
      > output,
      ResourceNode target,
      double cost
    ) {
      output.accept(new AnytimeRepairingAStar.Transition<>(
        target,
        cost,
        target
      ));
    }
  }
}
