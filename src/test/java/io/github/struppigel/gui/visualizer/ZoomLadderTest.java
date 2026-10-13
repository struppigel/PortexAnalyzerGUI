package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.visualizer.ZoomLadder.ZoomLevel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ZoomLadderTest {

    @Test
    void fromWholeFileToHex() {
        long length = 50L * 1024 * 1024;
        List<ZoomLevel> ladder = ZoomLadder.create(length, 200, 600);
        ZoomLevel first = ladder.get(0);

        // the whole file fits into the view at the first level
        long rows = (length + first.bytesPerRow() - 1) / first.bytesPerRow();
        assertTrue(rows <= 600 / first.cellHeight, first.toString());
        assertTrue(ladder.get(ladder.size() - 1).hex);
        for (int i = 0; i < ladder.size() - 1; i++) {
            ZoomLevel level = ladder.get(i);
            assertFalse(level.hex);
            assertEquals(1, Long.bitCount(level.bytesPerCell), "power of two: " + level);
            ZoomLevel next = ladder.get(i + 1);
            if (!next.hex) {
                // every step shows at most half as many bytes per row of pixels, magnified levels a quarter,
                // because their cells grow in both directions
                double factor = (double) level.bytesPerRow() / level.cellHeight
                        / ((double) next.bytesPerRow() / next.cellHeight);
                assertTrue(factor == 2.0 || factor == 4.0, level + " -> " + next + ": " + factor);
            }
        }
    }

    @Test
    void magnifiedLevelsKeepTheTrackWidth() {
        List<ZoomLevel> ladder = ZoomLadder.create(100_000, 256, 600);
        int width = ladder.get(0).columns * ladder.get(0).cellWidth;
        for (ZoomLevel level : ladder) {
            if (!level.hex) {
                assertEquals(width, level.columns * level.cellWidth, level.toString());
            }
        }
    }

    @Test
    void smallFilesStartAtSingleBytes() {
        List<ZoomLevel> ladder = ZoomLadder.create(268, 200, 600);
        assertEquals(1, ladder.get(0).bytesPerCell);
        assertTrue(ladder.get(ladder.size() - 1).hex);
    }

    @Test
    void closestLevelOfAnotherLadder() {
        List<ZoomLevel> ladder = ZoomLadder.create(1_000_000, 200, 600);
        for (int i = 0; i < ladder.size(); i++) {
            assertEquals(i, ZoomLadder.closest(ladder, ladder.get(i)));
        }
        List<ZoomLevel> wider = ZoomLadder.create(1_000_000, 400, 600);
        assertEquals(wider.size() - 1, ZoomLadder.closest(wider, ladder.get(ladder.size() - 1)));
    }
}
