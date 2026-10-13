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

import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.engine.Viewport;
import io.github.struppigel.tools.visualizer.render.RenderStyle;
import io.github.struppigel.tools.visualizer.render.Track;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rendering helpers shared by the visualizer components.
 */
final class RenderSupport {

    /**
     * Renders tiles and overview images in the background. The renderers of PortEx are thread-safe and never touch
     * the PEData, so several images can be rendered at once. Daemon threads, so that they never keep the GUI alive.
     */
    static final ExecutorService RENDER_POOL = Executors.newFixedThreadPool(2, new java.util.concurrent.ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "visualizer-render-" + count.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private RenderSupport() {
    }

    /**
     * Renders one track of the viewport into a new image.
     *
     * @return the image, or null if the viewport has no pixels
     */
    static BufferedImage renderTrack(Track track, FileVisualization vis, Viewport viewport, int cellWidth,
                                     RenderStyle style) {
        int width = viewport.getMapping().getColumns() * cellWidth;
        int height = viewport.getRowCount() * viewport.getCellHeight();
        if (width <= 0 || height <= 0) {
            return null;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        track.renderer().render(vis, viewport, cellWidth, style, argb, width);
        return image;
    }

    /**
     * @return true if the track shows byte statistics, which may still be computed
     */
    static boolean usesStatistics(Track track) {
        return track == Track.BYTEPLOT || track == Track.ENTROPY;
    }
}
