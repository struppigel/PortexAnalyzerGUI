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

import io.github.struppigel.tools.visualizer.engine.ByteStatistics;
import io.github.struppigel.tools.visualizer.render.RenderStyle;
import io.github.struppigel.tools.visualizer.render.Track;

import java.awt.image.BufferedImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The rendered tiles of the detail view, least recently used ones are dropped when the cache gets too large.
 * Only used in the event dispatch thread.
 */
final class TileCache {

    static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;

    private final long maxBytes;
    private final LinkedHashMap<Key, Tile> tiles = new LinkedHashMap<>(64, 0.75f, true);
    private long bytes = 0;

    TileCache() {
        this(DEFAULT_MAX_BYTES);
    }

    TileCache(long maxBytes) {
        this.maxBytes = maxBytes;
    }

    Tile get(Key key) {
        return tiles.get(key);
    }

    void put(Key key, Tile tile) {
        Tile old = tiles.put(key, tile);
        if (old != null) {
            bytes -= old.bytes();
        }
        bytes += tile.bytes();
        Iterator<Map.Entry<Key, Tile>> it = tiles.entrySet().iterator();
        // the new tile is the most recently used one and is never dropped
        while (bytes > maxBytes && tiles.size() > 1 && it.hasNext()) {
            Map.Entry<Key, Tile> eldest = it.next();
            bytes -= eldest.getValue().bytes();
            it.remove();
        }
    }

    void clear() {
        tiles.clear();
        bytes = 0;
    }

    int size() {
        return tiles.size();
    }

    long getBytes() {
        return bytes;
    }

    /**
     * Identifies a tile: the rows of one track at one zoom level of one file.
     */
    static final class Key {
        final int generation;
        final Track track;
        final int columns;
        final long bytesPerCell;
        final int cellWidth;
        final int cellHeight;
        final RenderStyle style;
        final long tileIndex;

        Key(int generation, Track track, int columns, long bytesPerCell, int cellWidth, int cellHeight,
            RenderStyle style, long tileIndex) {
            this.generation = generation;
            this.track = track;
            this.columns = columns;
            this.bytesPerCell = bytesPerCell;
            this.cellWidth = cellWidth;
            this.cellHeight = cellHeight;
            this.style = style;
            this.tileIndex = tileIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key k = (Key) o;
            return generation == k.generation && track == k.track && columns == k.columns
                    && bytesPerCell == k.bytesPerCell && cellWidth == k.cellWidth && cellHeight == k.cellHeight
                    && tileIndex == k.tileIndex && style.equals(k.style);
        }

        @Override
        public int hashCode() {
            return Objects.hash(generation, track, columns, bytesPerCell, cellWidth, cellHeight, style, tileIndex);
        }
    }

    /**
     * A rendered tile.
     */
    static final class Tile {
        final BufferedImage image;
        /** false if cells were hatched because their statistics were not computed yet */
        final boolean complete;
        /** the computed length of the statistics when the tile was rendered */
        final long computedLength;
        final long startOffset;

        Tile(BufferedImage image, boolean complete, long computedLength, long startOffset) {
            this.image = image;
            this.complete = complete;
            this.computedLength = computedLength;
            this.startOffset = startOffset;
        }

        long bytes() {
            return 4L * image.getWidth() * image.getHeight();
        }

        /**
         * @return true if statistics were computed for cells of this tile since it was rendered
         */
        boolean isOutdated(ByteStatistics statistics) {
            return !complete && statistics.getComputedLength() > Math.max(computedLength, startOffset);
        }
    }
}
