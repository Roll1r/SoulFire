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
package com.soulfiremc.server.renderer;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class RenderDebugTraceTest {
  @Test
  void restoresOuterTraceAfterNestedRenderFails() throws IOException {
    var baseline = RenderDebugTrace.current();
    var outer = RenderDebugTrace.createForced(1, 1, 1, 0.0F, 0.0F);
    var inner = RenderDebugTrace.createForced(1, 1, 1, 0.0F, 0.0F);
    var failure = new IOException();

    var result = outer.call(() -> {
      RenderDebugTrace.current().chunkConsidered();
      assertSame(failure, assertThrows(IOException.class, () -> inner.call(() -> {
        RenderDebugTrace.current().chunkConsidered();
        throw failure;
      })));
      RenderDebugTrace.current().chunkConsidered();
      return RenderDebugTrace.current();
    });

    assertSame(outer, result);
    assertEquals(2, outer.snapshot().chunksConsidered());
    assertEquals(1, inner.snapshot().chunksConsidered());
    assertSame(baseline, RenderDebugTrace.current());
  }

  @Test
  void disabledNestedTraceDoesNotRecordIntoOuterRender() {
    var disabled = RenderDebugTrace.current();
    var outer = RenderDebugTrace.createForced(1, 1, 1, 0.0F, 0.0F);

    outer.run(() -> {
      disabled.run(() -> RenderDebugTrace.current().chunkConsidered());
      RenderDebugTrace.current().chunkConsidered();
    });

    assertEquals(1, outer.snapshot().chunksConsidered());
    assertEquals(0, disabled.snapshot().chunksConsidered());
    assertSame(disabled, RenderDebugTrace.current());
  }
}
