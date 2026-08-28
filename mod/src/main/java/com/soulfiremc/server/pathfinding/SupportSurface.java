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

import net.minecraft.world.phys.AABB;

/// A canonical collision surface inside the block that contains the player's
/// feet. Coordinates use fixed-point units to keep graph identity stable.
public record SupportSurface(
  short height,
  short minX,
  short minZ,
  short maxX,
  short maxZ
) {
  public static final int SCALE = 4_096;
  public static final SupportSurface FLOOR = new SupportSurface(
    (short) 0,
    (short) 0,
    (short) 0,
    (short) SCALE,
    (short) SCALE
  );
  public static final SupportSurface NONE = new SupportSurface(
    Short.MIN_VALUE,
    (short) 0,
    (short) 0,
    (short) 0,
    (short) 0
  );

  public static SupportSurface fromBox(AABB box) {
    return new SupportSurface(
      quantize(box.maxY),
      quantize(box.minX),
      quantize(box.minZ),
      quantize(box.maxX),
      quantize(box.maxZ)
    );
  }

  public double heightAsDouble() {
    return height == Short.MIN_VALUE
      ? Double.NaN
      : (double) height / SCALE;
  }

  public boolean contains(double x, double z) {
    var fixedX = quantize(x);
    var fixedZ = quantize(z);
    return fixedX >= minX
      && fixedX <= maxX
      && fixedZ >= minZ
      && fixedZ <= maxZ;
  }

  private static short quantize(double value) {
    var fixed = Math.round(value * SCALE);
    return (short) Math.clamp(fixed, Short.MIN_VALUE, Short.MAX_VALUE);
  }
}
