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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/// A reusable Anytime Repairing A* search session.
///
/// The state graph and goal must remain stable for the lifetime of the
/// session. Search records retain their best costs between epsilon stages.
/// Improvements to records already expanded in the current stage enter
/// `INCONS` and are repaired after epsilon decreases.
final class AnytimeRepairingAStar<S, E> {
  private static final double COST_TOLERANCE = 1.0E-9;
  private final Domain<S, E> domain;
  private final Configuration configuration;
  private final Map<S, SearchRecord<S, E>> records = new HashMap<>();
  private final Map<Object, List<SearchRecord<S, E>>> paretoFronts =
    new HashMap<>();
  private final Set<SearchRecord<S, E>> open = new LinkedHashSet<>();
  private final Set<SearchRecord<S, E>> closed = new LinkedHashSet<>();
  private final Set<SearchRecord<S, E>> inconsistent =
    new LinkedHashSet<>();
  private final PriorityQueue<OpenEntry<S, E>> openHeap =
    new PriorityQueue<>(Comparator
      .<OpenEntry<S, E>>comparingDouble(OpenEntry::key)
      .thenComparingDouble(entry -> entry.record().heuristic)
      .thenComparingLong(OpenEntry::sequence));
  private final List<Double> incumbentCosts = new ArrayList<>();
  private final double startHeuristic;
  private long sequence;
  private long expandedStates;
  private long generatedTransitions;
  private long reachedBoundaries;
  private long progressiveBoundaries;
  private long validBoundaries;
  private long firstProgressBoundaryExpansion = -1;
  private int repairIterations;
  private int repairedInconsistentStates;
  private @Nullable Solution<S, E> incumbent;
  private @Nullable PartialCandidate<S, E> bestBoundary;
  private @Nullable SearchRecord<S, E> blockedStart;
  private @Nullable SearchRecord<S, E> closestRecord;
  private @Nullable SearchRecord<S, E> closestExpandedRecord;
  private double epsilon;
  private double bestCertifiedBound = Double.POSITIVE_INFINITY;

  AnytimeRepairingAStar(
    S start,
    Domain<S, E> domain,
    Configuration configuration
  ) {
    this.domain = domain;
    this.configuration = configuration;
    this.epsilon = configuration.initialEpsilon();

    var startRecord = record(start);
    this.startHeuristic = startRecord.heuristic;
    startRecord.g = 0;
    startRecord.active = true;
    paretoFront(start).add(startRecord);
    if (domain.isGoal(start, null)) {
      updateIncumbent(List.of(), start, 0);
    } else {
      offerOpen(startRecord);
    }
  }

  Outcome<S, E> search() {
    long acceptedAt = Long.MAX_VALUE;

    while (true) {
      var stageDeadline = acceptedAt == Long.MAX_VALUE
        ? configuration.deadlineNanos()
        : Math.min(
          configuration.deadlineNanos(),
          saturatedAdd(acceptedAt, configuration.improvementBudgetNanos())
      );
      var termination = improvePath(stageDeadline);
      var certifiedBound = certifiedBound(termination);
      var qualified = incumbent != null
        && certifiedBound <= configuration.requiredQualityBound()
          + COST_TOLERANCE;

      if (qualified && acceptedAt == Long.MAX_VALUE) {
        acceptedAt = System.nanoTime();
      }

      if (
        termination == Termination.EXHAUSTED
          && incumbent == null
          && !inconsistent.isEmpty()
      ) {
        prepareRepair(epsilon);
        continue;
      }

      if (termination == Termination.CANCELLED) {
        return outcome(Status.INTERRUPTED, 0);
      }
      if (termination != Termination.STAGE_COMPLETE) {
        if (qualified) {
          return outcome(Status.FOUND, certifiedBound);
        }
        if (incumbent != null) {
          return outcome(
            Status.QUALITY_BOUND_NOT_MET,
            certifiedBound,
            stopReason(termination)
          );
        }
        if (bestBoundary != null) {
          return partialOutcome(termination);
        }
        if (
          blockedStart != null
            && (termination == Termination.EXHAUSTED
            || termination == Termination.FRONTIER_LIMIT)
        ) {
          return worldDataPendingOutcome();
        }
        var status = switch (termination) {
          case EXHAUSTED -> Status.UNREACHABLE;
          case CANCELLED -> Status.INTERRUPTED;
          case DEADLINE, EXPANSION_BUDGET, FRONTIER_LIMIT -> Status.SEARCH_LIMIT;
          case STAGE_COMPLETE -> throw new IllegalStateException();
        };
        return outcome(status, 0, stopReason(termination));
      }

      if (incumbent == null) {
        throw new IllegalStateException(
          "ARA* completed an epsilon stage without an incumbent"
        );
      }
      if (
        epsilon <= 1 + COST_TOLERANCE
          || (open.isEmpty() && inconsistent.isEmpty())
          || (qualified && configuration.improvementBudgetNanos() == 0)
          || (qualified && System.nanoTime() >= stageDeadline)
      ) {
        return outcome(
          qualified ? Status.FOUND : Status.QUALITY_BOUND_NOT_MET,
          certifiedBound
        );
      }

      var epsilonFloor = qualified
        ? 1
        : configuration.requiredQualityBound();
      var nextEpsilon = Math.max(
        epsilonFloor,
        Math.min(
          epsilon - configuration.epsilonStep(),
          certifiedBound
        )
      );
      if (nextEpsilon >= epsilon - COST_TOLERANCE) {
        nextEpsilon = Math.max(
          epsilonFloor,
          epsilon - configuration.epsilonStep()
        );
      }
      if (nextEpsilon >= epsilon - COST_TOLERANCE) {
        return outcome(
          qualified ? Status.FOUND : Status.QUALITY_BOUND_NOT_MET,
          certifiedBound
        );
      }
      prepareRepair(nextEpsilon);
    }
  }

  private Termination improvePath(long stageDeadline) {
    while (true) {
      if (configuration.cancelled().getAsBoolean()) {
        return Termination.CANCELLED;
      }
      var minimumKey = minimumOpenKey();
      if (
        incumbent != null
          && incumbent.cost() <= minimumKey + COST_TOLERANCE
      ) {
        return Termination.STAGE_COMPLETE;
      }
      if (System.nanoTime() >= stageDeadline) {
        return Termination.DEADLINE;
      }
      if (expandedStates >= configuration.maximumExpandedStates()) {
        return Termination.EXPANSION_BUDGET;
      }

      var current = pollOpen();
      if (current == null) {
        return Termination.EXHAUSTED;
      }
      closed.add(current);
      expandedStates++;
      if (
        closestExpandedRecord == null
          || current.heuristic < closestExpandedRecord.heuristic
      ) {
        closestExpandedRecord = current;
      }

      var reachedBoundary = domain.expand(
        current.state,
        transition -> relax(current, transition)
      );
      if (reachedBoundary) {
        reachedBoundaries++;
        considerBlockedStart(current);
      }
      if (
        reachedBoundary
          && current.parent != null
          && current.heuristic < startHeuristic
      ) {
        progressiveBoundaries++;
        if (domain.isValidFrontier(current.state, current.incomingEdge)) {
          validBoundaries++;
          if (firstProgressBoundaryExpansion < 0) {
            firstProgressBoundaryExpansion = expandedStates;
          }
          considerBoundary(current);
        }
      }
      if (
        incumbent == null
          && firstProgressBoundaryExpansion >= 0
          && expandedStates - firstProgressBoundaryExpansion
            >= configuration.maximumExpansionsAfterFrontier()
      ) {
        return Termination.FRONTIER_LIMIT;
      }
    }
  }

  private void relax(
    SearchRecord<S, E> current,
    Transition<S, E> transition
  ) {
    generatedTransitions++;
    var candidateCost = current.g + transition.cost();
    if (!Double.isFinite(candidateCost)) {
      return;
    }

    if (domain.isGoal(transition.state(), transition.edge())) {
      var path = reconstruct(current);
      path.add(transition.edge());
      updateIncumbent(path, transition.state(), candidateCost);
    }

    var exactRecord = records.get(transition.state());
    if (isDominated(transition.state(), candidateCost, exactRecord)) {
      return;
    }
    if (
      exactRecord != null
        && exactRecord.active
        && exactRecord.g <= candidateCost + COST_TOLERANCE
    ) {
      return;
    }

    var successor = exactRecord == null
      ? record(transition.state())
      : exactRecord;
    if (!successor.active) {
      successor.active = true;
      paretoFront(successor.state).add(successor);
    }
    successor.g = candidateCost;
    successor.parent = current;
    successor.incomingEdge = transition.edge();
    successor.revision++;
    removeDominatedBy(successor);

    if (closed.contains(successor)) {
      inconsistent.add(successor);
    } else {
      offerOpen(successor);
    }
  }

  private boolean isDominated(
    S state,
    double candidateCost,
    @Nullable SearchRecord<S, E> exactRecord
  ) {
    for (var existing : paretoFront(state)) {
      if (
        existing != exactRecord
          && existing.active
          && existing.g <= candidateCost + COST_TOLERANCE
          && domain.dominates(existing.state, state)
      ) {
        return true;
      }
    }
    return false;
  }

  private void removeDominatedBy(SearchRecord<S, E> candidate) {
    var front = paretoFront(candidate.state);
    for (var existing : List.copyOf(front)) {
      if (
        existing != candidate
          && existing.active
          && candidate.g <= existing.g + COST_TOLERANCE
          && domain.dominates(candidate.state, existing.state)
      ) {
        deactivate(existing);
      }
    }
  }

  private void deactivate(SearchRecord<S, E> record) {
    record.active = false;
    record.revision++;
    paretoFront(record.state).remove(record);
    open.remove(record);
    closed.remove(record);
    inconsistent.remove(record);
  }

  private void considerBoundary(SearchRecord<S, E> record) {
    var candidate = new PartialCandidate<>(
      List.copyOf(reconstruct(record)),
      record.state,
      record.g,
      record.heuristic
    );
    if (
      bestBoundary == null
        || candidate.cost() + candidate.heuristic()
          < bestBoundary.cost() + bestBoundary.heuristic()
      ) {
      bestBoundary = candidate;
    }
  }

  private void considerBlockedStart(SearchRecord<S, E> record) {
    if (record.parent == null) {
      blockedStart = record;
    }
  }

  private void updateIncumbent(
    List<E> path,
    S endpoint,
    double cost
  ) {
    if (incumbent != null && incumbent.cost() <= cost + COST_TOLERANCE) {
      return;
    }
    incumbent = new Solution<>(List.copyOf(path), endpoint, cost);
    incumbentCosts.add(cost);
  }

  private double certifiedBound(Termination termination) {
    if (incumbent == null) {
      return Double.POSITIVE_INFINITY;
    }
    if (incumbent.cost() == 0) {
      bestCertifiedBound = 1;
      return 1;
    }

    var lowerBound = Double.POSITIVE_INFINITY;
    var hasFrontier = false;
    for (var record : open) {
      if (record.active) {
        hasFrontier = true;
        lowerBound = Math.min(lowerBound, record.g + record.heuristic);
      }
    }
    for (var record : inconsistent) {
      if (record.active) {
        hasFrontier = true;
        lowerBound = Math.min(lowerBound, record.g + record.heuristic);
      }
    }
    if (!hasFrontier) {
      bestCertifiedBound = 1;
      return 1;
    }
    var frontierBound = lowerBound > 0 && Double.isFinite(lowerBound)
      ? Math.max(1, incumbent.cost() / lowerBound)
      : Double.POSITIVE_INFINITY;
    if (termination == Termination.STAGE_COMPLETE) {
      bestCertifiedBound = Math.min(
        bestCertifiedBound,
        Math.min(epsilon, frontierBound)
      );
    }
    return Math.min(bestCertifiedBound, frontierBound);
  }

  private void prepareRepair(double nextEpsilon) {
    epsilon = nextEpsilon;
    repairIterations++;
    for (var record : inconsistent) {
      if (record.active) {
        open.add(record);
        repairedInconsistentStates++;
      }
    }
    inconsistent.clear();
    closed.clear();
    openHeap.clear();
    for (var record : open) {
      if (record.active) {
        addHeapEntry(record);
      }
    }
  }

  private SearchRecord<S, E> record(S state) {
    var result = records.computeIfAbsent(
      state,
      value -> new SearchRecord<>(
        value,
        checkedHeuristic(domain.heuristic(value))
      )
    );
    if (
      closestRecord == null
        || result.heuristic < closestRecord.heuristic
    ) {
      closestRecord = result;
    }
    return result;
  }

  BoundaryDiagnostics<S> boundaryDiagnostics() {
    var closest = closestRecord;
    if (closest == null) {
      throw new IllegalStateException("The search has no start record");
    }
    var closestExpanded = closestExpandedRecord;
    if (closestExpanded == null) {
      throw new IllegalStateException("The search expanded no records");
    }
    return new BoundaryDiagnostics<>(
      closest.state,
      closest.heuristic,
      closestExpanded.state,
      closestExpanded.heuristic,
      reachedBoundaries,
      progressiveBoundaries,
      validBoundaries
    );
  }

  private List<SearchRecord<S, E>> paretoFront(S state) {
    return paretoFronts.computeIfAbsent(
      domain.dominanceKey(state),
      _ -> new ArrayList<>()
    );
  }

  private void offerOpen(SearchRecord<S, E> record) {
    open.add(record);
    addHeapEntry(record);
  }

  private void addHeapEntry(SearchRecord<S, E> record) {
    openHeap.add(new OpenEntry<>(
      record.g + epsilon * record.heuristic,
      sequence++,
      record.revision,
      record
    ));
  }

  private double minimumOpenKey() {
    discardInvalidHeapEntries();
    return openHeap.isEmpty()
      ? Double.POSITIVE_INFINITY
      : openHeap.element().key();
  }

  private @Nullable SearchRecord<S, E> pollOpen() {
    discardInvalidHeapEntries();
    if (openHeap.isEmpty()) {
      return null;
    }
    var record = openHeap.remove().record();
    open.remove(record);
    return record;
  }

  private void discardInvalidHeapEntries() {
    while (!openHeap.isEmpty()) {
      var entry = openHeap.element();
      if (
        entry.record().active
          && open.contains(entry.record())
          && entry.revision() == entry.record().revision
      ) {
        return;
      }
      openHeap.remove();
    }
  }

  private ArrayList<E> reconstruct(SearchRecord<S, E> endpoint) {
    var path = new ArrayList<E>();
    for (
      SearchRecord<S, E> current = endpoint;
      current != null && current.incomingEdge != null;
      current = current.parent
    ) {
      path.addFirst(current.incomingEdge);
    }
    return path;
  }

  private Outcome<S, E> partialOutcome(Termination termination) {
    var boundary = bestBoundary;
    if (boundary == null) {
      throw new IllegalStateException("No frontier is available");
    }
    return new Outcome<>(
      Status.PARTIAL,
      boundary.path(),
      boundary.endpoint(),
      boundary.cost(),
      0,
      epsilon,
      repairIterations,
      repairedInconsistentStates,
      expandedStates,
      generatedTransitions,
      List.copyOf(incumbentCosts),
      switch (termination) {
        case FRONTIER_LIMIT, EXHAUSTED -> StopReason.FRONTIER;
        case DEADLINE -> StopReason.DEADLINE;
        case EXPANSION_BUDGET -> StopReason.EXPANSION_BUDGET;
        case CANCELLED, STAGE_COMPLETE -> throw new IllegalStateException();
      }
    );
  }

  private Outcome<S, E> worldDataPendingOutcome() {
    var boundary = blockedStart;
    if (boundary == null) {
      throw new IllegalStateException("No blocked frontier is available");
    }
    return new Outcome<>(
      Status.WORLD_DATA_PENDING,
      List.of(),
      boundary.state,
      0,
      0,
      epsilon,
      repairIterations,
      repairedInconsistentStates,
      expandedStates,
      generatedTransitions,
      List.copyOf(incumbentCosts),
      StopReason.FRONTIER
    );
  }

  private Outcome<S, E> outcome(Status status, double certifiedBound) {
    return outcome(status, certifiedBound, StopReason.NONE);
  }

  private Outcome<S, E> outcome(
    Status status,
    double certifiedBound,
    StopReason stopReason
  ) {
    var solution = incumbent;
    return new Outcome<>(
      status,
      solution == null ? List.of() : solution.path(),
      solution == null ? null : solution.endpoint(),
      solution == null ? 0 : solution.cost(),
      certifiedBound,
      epsilon,
      repairIterations,
      repairedInconsistentStates,
      expandedStates,
      generatedTransitions,
      List.copyOf(incumbentCosts),
      stopReason
    );
  }

  private static StopReason stopReason(Termination termination) {
    return switch (termination) {
      case STAGE_COMPLETE, EXHAUSTED, CANCELLED -> StopReason.NONE;
      case DEADLINE -> StopReason.DEADLINE;
      case EXPANSION_BUDGET -> StopReason.EXPANSION_BUDGET;
      case FRONTIER_LIMIT -> StopReason.FRONTIER;
    };
  }

  private static double checkedHeuristic(double heuristic) {
    if (!Double.isFinite(heuristic) || heuristic < 0) {
      throw new IllegalArgumentException(
        "The ARA* heuristic must be finite and non-negative"
      );
    }
    return heuristic;
  }

  private static long saturatedAdd(long left, long right) {
    try {
      return Math.addExact(left, right);
    } catch (ArithmeticException _) {
      return Long.MAX_VALUE;
    }
  }

  interface Domain<S, E> {
    double heuristic(S state);

    boolean isGoal(S state, @Nullable E incomingEdge);

    boolean expand(S state, Consumer<Transition<S, E>> output);

    Object dominanceKey(S state);

    boolean dominates(S left, S right);

    default boolean isValidFrontier(
      S state,
      @Nullable E incomingEdge
    ) {
      return incomingEdge != null;
    }
  }

  record Transition<S, E>(S state, double cost, E edge) {
    Transition {
      if (!Double.isFinite(cost) || cost < 0) {
        throw new IllegalArgumentException(
          "ARA* transition cost must be finite and non-negative"
        );
      }
    }
  }

  record Configuration(
    double initialEpsilon,
    double requiredQualityBound,
    double epsilonStep,
    long deadlineNanos,
    long improvementBudgetNanos,
    long maximumExpandedStates,
    long maximumExpansionsAfterFrontier,
    BooleanSupplier cancelled
  ) {
    Configuration {
      if (!Double.isFinite(initialEpsilon) || initialEpsilon < 1) {
        throw new IllegalArgumentException(
          "initialEpsilon must be finite and at least 1"
        );
      }
      if (
        !Double.isFinite(requiredQualityBound)
          || requiredQualityBound < 1
          || requiredQualityBound > initialEpsilon
      ) {
        throw new IllegalArgumentException(
          "requiredQualityBound must be from 1 through initialEpsilon"
        );
      }
      if (!Double.isFinite(epsilonStep) || epsilonStep <= 0) {
        throw new IllegalArgumentException(
          "epsilonStep must be finite and positive"
        );
      }
      if (
        improvementBudgetNanos < 0
          || maximumExpandedStates < 1
          || maximumExpansionsAfterFrontier < 1
      ) {
        throw new IllegalArgumentException("ARA* budgets are invalid");
      }
    }
  }

  enum Status {
    FOUND,
    PARTIAL,
    WORLD_DATA_PENDING,
    UNREACHABLE,
    SEARCH_LIMIT,
    INTERRUPTED,
    QUALITY_BOUND_NOT_MET
  }

  enum StopReason {
    NONE,
    FRONTIER,
    DEADLINE,
    EXPANSION_BUDGET
  }

  record Outcome<S, E>(
    Status status,
    List<E> path,
    @Nullable S endpoint,
    double cost,
    double certifiedQualityBound,
    double finalEpsilon,
    int repairIterations,
    int repairedInconsistentStates,
    long expandedStates,
    long generatedTransitions,
    List<Double> incumbentCosts,
    StopReason stopReason
  ) {
    Outcome {
      path = List.copyOf(path);
      incumbentCosts = List.copyOf(incumbentCosts);
    }
  }

  record BoundaryDiagnostics<S>(
    S closestState,
    double closestHeuristic,
    S closestExpandedState,
    double closestExpandedHeuristic,
    long reachedBoundaries,
    long progressiveBoundaries,
    long validBoundaries
  ) {}

  private enum Termination {
    STAGE_COMPLETE,
    EXHAUSTED,
    CANCELLED,
    DEADLINE,
    EXPANSION_BUDGET,
    FRONTIER_LIMIT
  }

  private static final class SearchRecord<S, E> {
    private final S state;
    private final double heuristic;
    private double g = Double.POSITIVE_INFINITY;
    private long revision;
    private boolean active;
    private @Nullable SearchRecord<S, E> parent;
    private @Nullable E incomingEdge;

    private SearchRecord(S state, double heuristic) {
      this.state = state;
      this.heuristic = heuristic;
    }
  }

  private record OpenEntry<S, E>(
    double key,
    long sequence,
    long revision,
    SearchRecord<S, E> record
  ) {}

  private record Solution<S, E>(
    List<E> path,
    S endpoint,
    double cost
  ) {}

  private record PartialCandidate<S, E>(
    List<E> path,
    S endpoint,
    double cost,
    double heuristic
  ) {}
}
