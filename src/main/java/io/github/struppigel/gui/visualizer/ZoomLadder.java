/**
 * *****************************************************************************
 * Copyright 2026 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.visualizer.engine.ViewMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The zoom levels of the detail view, from the whole file down to single bytes as hex values.
 * <p>
 * The coarse levels halve the bytes per cell, which are powers of two so that cells align with the blocks of the
 * byte statistics. At one byte per cell the cells grow, with fewer columns, so that the tracks keep their width.
 * The last level shows the bytes as hex and ASCII.
 */
final class ZoomLadder {

    static final int HEX_COLUMNS = 16;
    static final int HEX_CELL_HEIGHT = 14;
    /** cell width of byte plot and entropy next to the hex values */
    static final int HEX_SIDE_CELL_WIDTH = 4;
    private static final int MIN_COLUMNS = 16;
    private static final int MAX_COLUMNS = 128;
    private static final int MAX_MAGNIFIED_CELL = 16;

    private ZoomLadder() {
    }

    /**
     * One zoom level.
     */
    static final class ZoomLevel {
        final int columns;
        final long bytesPerCell;
        /** cell width of the colored tracks */
        final int cellWidth;
        final int cellHeight;
        final boolean hex;

        ZoomLevel(int columns, long bytesPerCell, int cellWidth, int cellHeight, boolean hex) {
            this.columns = columns;
            this.bytesPerCell = bytesPerCell;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.hex = hex;
        }

        long bytesPerRow() {
            return columns * bytesPerCell;
        }

        @Override
        public String toString() {
            return hex ? "hex" : bytesPerCell + " bytes per cell, " + columns + " columns, " + cellWidth + " px";
        }
    }

    /**
     * @param fileLength the size of the file
     * @param trackWidth the width available for one colored track
     * @param viewHeight the height of the view
     * @return the levels from the whole file to hex
     */
    static List<ZoomLevel> create(long fileLength, int trackWidth, int viewHeight) {
        return create(fileLength, trackWidth, viewHeight, HEX_COLUMNS);
    }

    /**
     * @param hexColumns the bytes per line of the hex level
     */
    static List<ZoomLevel> create(long fileLength, int trackWidth, int viewHeight, int hexColumns) {
        // a power of two of columns for round row offsets, cells as large as the track width allows
        int columns = clamp(highestPowerOfTwo(Math.max(1, trackWidth / 4)), MIN_COLUMNS, MAX_COLUMNS);
        int cell = clamp(trackWidth / columns, 2, 8);
        int visibleRows = Math.max(1, viewHeight / cell);
        long fit = ViewMapping.fitBytesPerCell(Math.max(1, fileLength), columns, visibleRows, true);

        List<ZoomLevel> levels = new ArrayList<>();
        for (long bytesPerCell = fit; bytesPerCell >= 1; bytesPerCell /= 2) {
            levels.add(new ZoomLevel(columns, bytesPerCell, cell, cell, false));
        }
        while (cell * 2 <= MAX_MAGNIFIED_CELL && columns / 2 >= MIN_COLUMNS) {
            columns /= 2;
            cell *= 2;
            levels.add(new ZoomLevel(columns, 1, cell, cell, false));
        }
        levels.add(new ZoomLevel(Math.max(1, hexColumns), 1, HEX_SIDE_CELL_WIDTH, HEX_CELL_HEIGHT, true));
        return Collections.unmodifiableList(levels);
    }

    /**
     * @return the index of the level in the ladder that is the most similar to the given level
     */
    static int closest(List<ZoomLevel> ladder, ZoomLevel level) {
        if (level.hex) {
            return ladder.size() - 1;
        }
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < ladder.size(); i++) {
            ZoomLevel candidate = ladder.get(i);
            if (candidate.hex) {
                continue;
            }
            // compare by bytes per pixel row, which is what the user perceives as zoom
            double distance = Math.abs(Math.log((double) candidate.bytesPerRow() / candidate.cellHeight)
                    - Math.log((double) level.bytesPerRow() / level.cellHeight));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    static int highestPowerOfTwo(int value) {
        return Integer.highestOneBit(Math.max(1, value));
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
