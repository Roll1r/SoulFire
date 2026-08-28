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

import com.google.common.base.Stopwatch;
import com.soulfiremc.server.pathfinding.cost.Costs;
import com.soulfiremc.server.pathfinding.execution.BlockPlaceAction;
import com.soulfiremc.server.pathfinding.execution.JumpAndPlaceBelowAction;
import com.soulfiremc.server.pathfinding.execution.WorldAction;
import com.soulfiremc.server.pathfinding.goals.GoalScorer;
import com.soulfiremc.server.pathfinding.graph.GraphInstructions;
import com.soulfiremc.server.pathfinding.graph.MinecraftGraph;
import com.soulfiremc.server.util.structs.CancellationToken;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

/// Finds routes with immutable labels and explicit bounded-suboptimal search.
/// The search never removes the frontier to force progress.
@Slf4j
public record RouteFinder(MinecraftGraph baseGraph, GoalScorer scorer, Executor executor) {
  private static final int MAX_NODES_AFTER_LEVEL_BOUNDARY = 2_048;
  private static final long IMPROVEMENT_BUDGET_MILLIS = 50;
  private static final double WEIGHT_STEP = 0.2;

  public RouteFinder(MinecraftGraph baseGraph, GoalScorer scorer) {
    this(baseGraph, scorer, ForkJoinPool.commonPool());
  }

  public CompletableFuture<RouteSearchResult> findRouteFuture(NodeState from) {
    var cancellationToken = new CancellationToken();
    var future = CompletableFuture.supplyAsync(
      () -> findRouteSync(from, cancellationToken),
      executor
    );
    future.whenComplete((_, _) -> {
      if (future.isCancelled()) {
        cancellationToken.cancel();
      }
    });
    return future;
  }

  private RouteSearchResult findRouteSync(
    NodeState suppliedStart,
    CancellationToken cancellationToken
  ) {
    var stopwatch = Stopwatch.createStarted();
    var constraint = baseGraph.pathConstraint();
    var mode = constraint.searchMode();
    var initialWeight = Math.min(
      mode.heuristicWeight(),
      constraint.maximumQualityBound()
    );
    var deadline = System.nanoTime()
      + TimeUnit.SECONDS.toNanos(constraint.expireTimeout());
    var start = baseGraph.snapshot().stateAt(
      suppliedStart.blockPosition(),
      suppliedStart.resources(),
      SupportOrigin.WORLD
    );

    var aggregate = SearchCounters.ZERO;
    var result = searchOnce(
      start,
      mode,
      initialWeight,
      deadline,
      cancellationToken
    );
    aggregate = aggregate.add(result.counters());
    var routeResult = result.result();

    if (routeResult instanceof FoundRouteResult found && initialWeight > 1) {
      var incumbent = found;
      var incumbentBound = initialWeight;
      var improvementDeadline = Math.min(
        deadline,
        System.nanoTime()
          + TimeUnit.MILLISECONDS.toNanos(IMPROVEMENT_BUDGET_MILLIS)
      );
      for (
        var weight = Math.max(1, initialWeight - WEIGHT_STEP);
        weight < incumbentBound && System.nanoTime() < improvementDeadline;
        weight = Math.max(1, weight - WEIGHT_STEP)
      ) {
        var improved = searchOnce(
          start,
          mode,
          weight,
          improvementDeadline,
          cancellationToken
        );
        aggregate = aggregate.add(improved.counters());
        if (
          improved.result() instanceof FoundRouteResult candidate
            && candidate.metadata().routeCost()
              .compareTo(incumbent.metadata().routeCost()) <= 0
        ) {
          incumbent = candidate;
          incumbentBound = weight;
        }
        if (weight == 1) {
          break;
        }
      }
      routeResult = withMetadata(
        incumbent,
        incumbent.metadata().withQualityBound(incumbentBound)
      );
    }

    stopwatch.stop();
    var finalMetadata = routeResult.metadata().withAggregate(
      aggregate,
      stopwatch.elapsed().toMillis()
    );
    routeResult = withMetadata(routeResult, finalMetadata);
    log.info(
      "Route search finished with {} after {}ms, {} expansions, quality bound {}, and {}/{} graph cache hits",
      routeResult.getClass().getSimpleName(),
      finalMetadata.elapsedMillis(),
      finalMetadata.expandedStates(),
      finalMetadata.qualityBound(),
      baseGraph.transitionCache().hits(),
      baseGraph.transitionCache().hits() + baseGraph.transitionCache().misses()
    );
    return routeResult;
  }

  private SearchAttempt searchOnce(
    NodeState start,
    RouteSearchMode searchMode,
    double heuristicWeight,
    long deadline,
    CancellationToken cancellationToken
  ) {
    var openSet = new PriorityQueue<MinecraftRouteNode>();
    var labels = new HashMap<StateIdentity, List<MinecraftRouteNode>>();
    var startHeuristic = heuristic(start.blockPosition(), List.of());
    var startNode = new MinecraftRouteNode(
      start,
      null,
      null,
      List.of(),
      RouteCost.ZERO,
      startHeuristic,
      heuristicWeight
    );
    addLabel(labels, startNode);
    openSet.add(startNode);

    var expandedStates = 0L;
    var generatedTransitions = 0L;
    var nodesAtFirstBoundary = -1L;
    MinecraftRouteNode bestProgress = startNode;
    MinecraftRouteNode bestBoundary = null;
    var boundaryReason = FrontierReason.NONE;

    while (!openSet.isEmpty()) {
      if (
        Thread.currentThread().isInterrupted()
          || cancellationToken.isCancelled()
      ) {
        return attempt(
          new SearchInterruptedResult(metadata(
            searchMode,
            heuristicWeight,
            RouteCost.ZERO,
            expandedStates,
            generatedTransitions,
            FrontierReason.NONE
          )),
          expandedStates,
          generatedTransitions
        );
      }
      if (System.nanoTime() >= deadline) {
        var deadlineFrontier = bestBoundary != null
          ? bestBoundary
          : progressing(startHeuristic, bestProgress)
          ? bestProgress
          : null;
        if (deadlineFrontier != null) {
          return attempt(
            partialResult(
              deadlineFrontier,
              searchMode,
              heuristicWeight,
              expandedStates,
              generatedTransitions,
              bestBoundary != null
                ? boundaryReason
                : FrontierReason.SEARCH_DEADLINE
            ),
            expandedStates,
            generatedTransitions
          );
        }
        return attempt(
          new NoRouteFoundResult(metadata(
            searchMode,
            heuristicWeight,
            RouteCost.ZERO,
            expandedStates,
            generatedTransitions,
            FrontierReason.SEARCH_DEADLINE
          )),
          expandedStates,
          generatedTransitions
        );
      }
      if (expandedStates >= baseGraph.pathConstraint().maximumExpandedStates()) {
        return attempt(
          new SearchLimitReachedResult(metadata(
            searchMode,
            heuristicWeight,
            bestProgress.routeCost(),
            expandedStates,
            generatedTransitions,
            FrontierReason.SEARCH_BUDGET
          )),
          expandedStates,
          generatedTransitions
        );
      }

      var current = openSet.remove();
      if (!isActiveLabel(labels, current)) {
        continue;
      }
      expandedStates++;

      if (scorer.isFinished(current)) {
        return attempt(
          new FoundRouteResult(
            reconstructPath(current),
            metadata(
              searchMode,
              heuristicWeight,
              current.routeCost(),
              expandedStates,
              generatedTransitions,
              FrontierReason.NONE
            )
          ),
          expandedStates,
          generatedTransitions
        );
      }

      var generation = new GenerationState(
        current,
        labels,
        openSet,
        bestProgress,
        generatedTransitions
      );
      var reachedLevelBoundary = baseGraph.insertActions(
        current.node(),
        instructions -> generateTransition(
          generation,
          instructions,
          heuristicWeight
        )
      );
      bestProgress = generation.bestProgress;
      generatedTransitions = generation.generatedTransitions;

      if (reachedLevelBoundary && progressing(startHeuristic, current)) {
        if (
          bestBoundary == null
            || comparePartialRouteCandidates(current, bestBoundary) < 0
        ) {
          bestBoundary = current;
          boundaryReason = FrontierReason.LEVEL_BOUNDARY;
        }
        if (nodesAtFirstBoundary < 0) {
          nodesAtFirstBoundary = expandedStates;
        }
      }
      if (
        nodesAtFirstBoundary >= 0
          && expandedStates - nodesAtFirstBoundary
            >= MAX_NODES_AFTER_LEVEL_BOUNDARY
      ) {
        return attempt(
          partialResult(
            bestBoundary,
            searchMode,
            heuristicWeight,
            expandedStates,
            generatedTransitions,
            boundaryReason
          ),
          expandedStates,
          generatedTransitions
        );
      }
    }

    if (bestBoundary != null) {
      return attempt(
        partialResult(
          bestBoundary,
          searchMode,
          heuristicWeight,
          expandedStates,
          generatedTransitions,
          boundaryReason
        ),
        expandedStates,
        generatedTransitions
      );
    }
    return attempt(
      new NoRouteFoundResult(metadata(
        searchMode,
        heuristicWeight,
        RouteCost.ZERO,
        expandedStates,
        generatedTransitions,
        FrontierReason.NONE
      )),
      expandedStates,
      generatedTransitions
    );
  }

  private void generateTransition(
    GenerationState generation,
    GraphInstructions instructions,
    double heuristicWeight
  ) {
    generation.generatedTransitions++;
    var current = generation.current;
    if (
      instructions.requiresOneBlock()
        && current.node().usableBlockItems() < 1
    ) {
      return;
    }

    var newBlockCount = current.node().usableBlockItems()
      + instructions.deltaUsableBlockItems();
    if (newBlockCount < 0) {
      return;
    }

    var resources = current.node().resources()
      .addUsableBlockItems(instructions.deltaUsableBlockItems());
    var targetState = baseGraph.snapshot().stateAt(
      instructions.blockPosition(),
      resources,
      supportOrigin(instructions)
    );
    var routeCost = current.routeCost().add(instructions.routeCost());
    var targetCost = heuristic(
      targetState.blockPosition(),
      instructions.actions()
    );
    var candidate = new MinecraftRouteNode(
      targetState,
      current,
      instructions.moveDirection(),
      instructions.actions(),
      routeCost,
      targetCost,
      heuristicWeight
    );
    if (!addLabel(generation.labels, candidate)) {
      return;
    }
    generation.openSet.add(candidate);
    if (compareProgress(candidate, generation.bestProgress) < 0) {
      generation.bestProgress = candidate;
    }
  }

  private double heuristic(
    SFVec3i position,
    List<WorldAction> actions
  ) {
    return scorer.computeScore(baseGraph, position, actions)
      * Costs.HEURISTIC_COST_PER_BLOCK;
  }

  private static SupportOrigin supportOrigin(GraphInstructions instructions) {
    var floor = instructions.blockPosition().sub(0, 1, 0);
    return instructions.actions().stream().anyMatch(action -> switch (action) {
      case BlockPlaceAction place -> place.blockPosition().equals(floor);
      case JumpAndPlaceBelowAction place ->
        place.blockPlacePosition().equals(floor);
      default -> false;
    }) ? SupportOrigin.PLACED : SupportOrigin.WORLD;
  }

  private static boolean addLabel(
    Map<StateIdentity, List<MinecraftRouteNode>> labels,
    MinecraftRouteNode candidate
  ) {
    var identity = StateIdentity.of(candidate.node());
    var stateLabels = labels.computeIfAbsent(
      identity,
      _ -> new ArrayList<>()
    );
    for (var existing : stateLabels) {
      if (
        existing.routeCost().noWorseThan(candidate.routeCost())
          && existing.node().resources()
            .dominates(candidate.node().resources())
      ) {
        return false;
      }
    }
    for (Iterator<MinecraftRouteNode> iterator = stateLabels.iterator(); iterator.hasNext(); ) {
      var existing = iterator.next();
      if (
        candidate.routeCost().noWorseThan(existing.routeCost())
          && candidate.node().resources()
            .dominates(existing.node().resources())
      ) {
        iterator.remove();
      }
    }
    stateLabels.add(candidate);
    return true;
  }

  private static boolean isActiveLabel(
    Map<StateIdentity, List<MinecraftRouteNode>> labels,
    MinecraftRouteNode node
  ) {
    var stateLabels = labels.get(StateIdentity.of(node.node()));
    return stateLabels != null && stateLabels.contains(node);
  }

  private static List<WorldAction> reconstructPath(
    MinecraftRouteNode current
  ) {
    var actions = new ArrayList<WorldAction>();
    for (
      MinecraftRouteNode element = current;
      element != null;
      element = element.parent()
    ) {
      for (var i = element.actions().size() - 1; i >= 0; i--) {
        actions.addFirst(element.actions().get(i));
      }
    }
    return List.copyOf(actions);
  }

  static int comparePartialRouteCandidates(
    MinecraftRouteNode left,
    MinecraftRouteNode right
  ) {
    return left.routeCost().compareEstimated(
      right.routeCost(),
      left.targetCost(),
      right.targetCost()
    );
  }

  static boolean isProgressingPartialRoute(
    double startScore,
    MinecraftRouteNode candidate
  ) {
    return progressing(startScore, candidate);
  }

  private static boolean progressing(
    double startScore,
    MinecraftRouteNode candidate
  ) {
    return candidate.parent() != null && candidate.targetCost() < startScore;
  }

  private static int compareProgress(
    MinecraftRouteNode left,
    MinecraftRouteNode right
  ) {
    var heuristicComparison = Double.compare(
      left.targetCost(),
      right.targetCost()
    );
    return heuristicComparison != 0
      ? heuristicComparison
      : left.routeCost().compareTo(right.routeCost());
  }

  private static PartialRouteResult partialResult(
    MinecraftRouteNode node,
    RouteSearchMode searchMode,
    double qualityBound,
    long expandedStates,
    long generatedTransitions,
    FrontierReason reason
  ) {
    return new PartialRouteResult(
      reconstructPath(node),
      node.node().blockPosition(),
      metadata(
        searchMode,
        qualityBound,
        node.routeCost(),
        expandedStates,
        generatedTransitions,
        reason
      )
    );
  }

  private static RouteSearchMetadata metadata(
    RouteSearchMode searchMode,
    double qualityBound,
    RouteCost routeCost,
    long expandedStates,
    long generatedTransitions,
    FrontierReason frontierReason
  ) {
    return new RouteSearchMetadata(
      searchMode,
      qualityBound,
      routeCost,
      expandedStates,
      generatedTransitions,
      0,
      frontierReason
    );
  }

  private static SearchAttempt attempt(
    RouteSearchResult result,
    long expandedStates,
    long generatedTransitions
  ) {
    return new SearchAttempt(
      result,
      new SearchCounters(expandedStates, generatedTransitions)
    );
  }

  private static RouteSearchResult withMetadata(
    RouteSearchResult result,
    RouteSearchMetadata metadata
  ) {
    return switch (result) {
      case FoundRouteResult found ->
        new FoundRouteResult(found.actions(), metadata);
      case PartialRouteResult partial ->
        new PartialRouteResult(
          partial.actions(),
          partial.endpoint(),
          metadata
        );
      case NoRouteFoundResult _ -> new NoRouteFoundResult(metadata);
      case SearchLimitReachedResult _ ->
        new SearchLimitReachedResult(metadata);
      case SearchInterruptedResult _ -> new SearchInterruptedResult(metadata);
    };
  }

  private static final class GenerationState {
    private final MinecraftRouteNode current;
    private final Map<StateIdentity, List<MinecraftRouteNode>> labels;
    private final PriorityQueue<MinecraftRouteNode> openSet;
    private MinecraftRouteNode bestProgress;
    private long generatedTransitions;

    private GenerationState(
      MinecraftRouteNode current,
      Map<StateIdentity, List<MinecraftRouteNode>> labels,
      PriorityQueue<MinecraftRouteNode> openSet,
      MinecraftRouteNode bestProgress,
      long generatedTransitions
    ) {
      this.current = current;
      this.labels = labels;
      this.openSet = openSet;
      this.bestProgress = bestProgress;
      this.generatedTransitions = generatedTransitions;
    }
  }

  private record StateIdentity(
    SFVec3i blockPosition,
    SupportSurface supportSurface,
    SupportOrigin supportOrigin,
    MovementMode movementMode
  ) {
    private static StateIdentity of(NodeState state) {
      return new StateIdentity(
        state.blockPosition(),
        state.supportSurface(),
        state.supportOrigin(),
        state.movementMode()
      );
    }
  }

  private record SearchAttempt(
    RouteSearchResult result,
    SearchCounters counters
  ) {}

  private record SearchCounters(
    long expandedStates,
    long generatedTransitions
  ) {
    private static final SearchCounters ZERO = new SearchCounters(0, 0);

    private SearchCounters add(SearchCounters other) {
      return new SearchCounters(
        expandedStates + other.expandedStates,
        generatedTransitions + other.generatedTransitions
      );
    }
  }

  public enum FrontierReason {
    NONE,
    LEVEL_BOUNDARY,
    SEARCH_DEADLINE,
    SEARCH_BUDGET
  }

  public record RouteSearchMetadata(
    RouteSearchMode searchMode,
    double qualityBound,
    RouteCost routeCost,
    long expandedStates,
    long generatedTransitions,
    long elapsedMillis,
    FrontierReason frontierReason
  ) {
    private RouteSearchMetadata withQualityBound(double value) {
      return new RouteSearchMetadata(
        searchMode,
        value,
        routeCost,
        expandedStates,
        generatedTransitions,
        elapsedMillis,
        frontierReason
      );
    }

    private RouteSearchMetadata withAggregate(
      SearchCounters counters,
      long elapsed
    ) {
      return new RouteSearchMetadata(
        searchMode,
        qualityBound,
        routeCost,
        counters.expandedStates,
        counters.generatedTransitions,
        elapsed,
        frontierReason
      );
    }
  }

  public sealed interface RouteSearchResult {
    RouteSearchMetadata metadata();
  }

  public record NoRouteFoundResult(
    RouteSearchMetadata metadata
  ) implements RouteSearchResult {}

  public record SearchInterruptedResult(
    RouteSearchMetadata metadata
  ) implements RouteSearchResult {}

  public record SearchLimitReachedResult(
    RouteSearchMetadata metadata
  ) implements RouteSearchResult {}

  public record FoundRouteResult(
    List<WorldAction> actions,
    RouteSearchMetadata metadata
  ) implements RouteSearchResult {}

  public record PartialRouteResult(
    List<WorldAction> actions,
    SFVec3i endpoint,
    RouteSearchMetadata metadata
  ) implements RouteSearchResult {}
}
