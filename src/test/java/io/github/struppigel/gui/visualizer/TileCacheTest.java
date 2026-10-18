package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.visualizer.render.RenderStyle;
import io.github.struppigel.tools.visualizer.render.Track;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.*;

class TileCacheTest {

    private static TileCache.Key key(int generation, long tileIndex) {
        return new TileCache.Key(generation, Track.STRUCTURE, 64, 16, 4, 4, RenderStyle.defaults(), tileIndex);
    }

    /** a tile of 10 x 10 pixels, 400 bytes */
    private static TileCache.Tile tile() {
        return new TileCache.Tile(new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB), true, 0, 0);
    }

    @Test
    void dropsLeastRecentlyUsedTiles() {
        TileCache cache = new TileCache(1000);
        cache.put(key(1, 0), tile());
        cache.put(key(1, 1), tile());
        assertNotNull(cache.get(key(1, 0))); // tile 0 is now more recently used than tile 1

        cache.put(key(1, 2), tile());
        assertEquals(2, cache.size());
        assertNull(cache.get(key(1, 1)));
        assertNotNull(cache.get(key(1, 0)));
        assertNotNull(cache.get(key(1, 2)));
        assertEquals(800, cache.getBytes());
    }

    @Test
    void keepsTheNewestTileEvenIfTooLarge() {
        TileCache cache = new TileCache(100);
        cache.put(key(1, 0), tile());
        assertEquals(1, cache.size());
        cache.put(key(1, 1), tile());
        assertEquals(1, cache.size());
        assertNotNull(cache.get(key(1, 1)));
    }

    @Test
    void replacingATileCountsItsBytesOnce() {
        TileCache cache = new TileCache(10_000);
        cache.put(key(1, 0), tile());
        cache.put(key(1, 0), tile());
        assertEquals(400, cache.getBytes());
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(0, cache.getBytes());
    }

    @Test
    void keysOfOtherFilesOrStylesDiffer() {
        assertEquals(key(1, 0), key(1, 0));
        assertEquals(key(1, 0).hashCode(), key(1, 0).hashCode());
        assertNotEquals(key(1, 0), key(2, 0));
        TileCache.Key otherStyle = new TileCache.Key(1, Track.STRUCTURE, 64, 16, 4, 4, RenderStyle.legacy(), 0);
        assertNotEquals(key(1, 0), otherStyle);
    }

    @Test
    void incompleteTilesAreOutdatedOnlyIfTheirStatisticsProgressed() throws Exception {
        java.io.File file = java.io.File.createTempFile("tilecache", ".bin");
        file.deleteOnExit();
        java.nio.file.Files.write(file.toPath(), new byte[100_000]);
        io.github.struppigel.tools.visualizer.engine.ByteStatistics stats =
                new io.github.struppigel.tools.visualizer.engine.ByteStatistics(file);
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        TileCache.Tile incomplete = new TileCache.Tile(image, false, 0, 50_000);
        assertFalse(incomplete.isOutdated(stats), "nothing computed yet");
        stats.compute(io.github.struppigel.tools.visualizer.engine.ProgressListener.NONE);
        assertTrue(incomplete.isOutdated(stats));
        assertFalse(new TileCache.Tile(image, true, 0, 0).isOutdated(stats));
    }
}
