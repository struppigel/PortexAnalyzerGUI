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

import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.visualizer.engine.ByteSourceClosedException;
import io.github.struppigel.tools.visualizer.engine.ByteStatistics;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.engine.ViewMapping;
import io.github.struppigel.tools.visualizer.engine.Viewport;
import io.github.struppigel.tools.visualizer.render.RenderStyle;
import io.github.struppigel.tools.visualizer.render.Track;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The whole file in one picture: byte plot, entropy and structure next to each other, scaled to the height of the
 * component. Shows which part of the file the detail view displays.
 */
public class OverviewStrip extends JComponent {

    private static final Logger LOGGER = LogManager.getLogger();
    static final List<Track> TRACKS = Arrays.asList(Track.BYTEPLOT, Track.ENTROPY, Track.STRUCTURE);
    private static final int CELL_SIZE = 2;
    private static final int GAP = 2;

    private final VisualizationModel model;
    private final int trackWidth;
    private final Executor executor;
    private final boolean showVisibleRange;

    private Rendered rendered;
    private RenderRequest pending;

    /**
     * @param model the visualization to show
     * @param trackWidth the width of each of the three tracks in pixels
     * @param showVisibleRange draw the range the detail view shows
     */
    public OverviewStrip(VisualizationModel model, int trackWidth, boolean showVisibleRange) {
        this(model, trackWidth, showVisibleRange, RenderSupport.RENDER_POOL);
    }

    OverviewStrip(VisualizationModel model, int trackWidth, boolean showVisibleRange, Executor executor) {
        this.model = model;
        this.trackWidth = trackWidth;
        this.executor = executor;
        this.showVisibleRange = showVisibleRange;
        setToolTipText(""); // enables tool tips, the text comes from getToolTipText(MouseEvent)
        setOpaque(true);
        setBackground(Color.black);
        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void dataChanged() {
                rendered = null;
                repaint();
            }

            @Override
            public void statisticsProgressed() {
                repaint(); // paint checks whether the picture is outdated
            }

            @Override
            public void styleChanged() {
                repaint();
            }

            @Override
            public void visibleRangeChanged() {
                if (showVisibleRange) {
                    repaint();
                }
            }

            @Override
            public void selectionChanged() {
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(TRACKS.size() * trackWidth + (TRACKS.size() - 1) * GAP, 100);
    }

    /**
     * @return the mapping of the overview for the current size, null if nothing can be shown
     */
    ViewMapping mapping() {
        FileVisualization vis = model.getVisualization();
        int rows = getHeight() / CELL_SIZE;
        if (vis == null || rows <= 0 || vis.getFileLength() <= 0) {
            return null;
        }
        int columns = Math.max(1, trackWidth / CELL_SIZE);
        long bytesPerCell = ViewMapping.fitBytesPerCell(vis.getFileLength(), columns, rows, false);
        return new ViewMapping(vis.getFileLength(), 0, bytesPerCell, columns);
    }

    /**
     * @return the file offset shown at the point, -1 if there is none
     */
    public long offsetAt(Point p) {
        ViewMapping mapping = mapping();
        if (mapping == null) {
            return -1;
        }
        int x = p.x % (trackWidth + GAP);
        if (x >= mapping.getColumns() * CELL_SIZE) {
            return -1;
        }
        Viewport viewport = new Viewport(mapping, 0, (int) mapping.getRowCount(), CELL_SIZE);
        return viewport.offsetAt(x, p.y, CELL_SIZE);
    }

    /**
     * @return the track at the point, null if there is none
     */
    public Track trackAt(Point p) {
        int index = p.x / (trackWidth + GAP);
        if (p.x < 0 || index >= TRACKS.size() || p.x % (trackWidth + GAP) >= trackWidth) {
            return null;
        }
        return TRACKS.get(index);
    }

    /**
     * @return the file offset of the first cell in the row at y, clamped to the file, -1 if nothing is shown
     */
    public long rowOffsetAt(int y) {
        ViewMapping mapping = mapping();
        if (mapping == null) {
            return -1;
        }
        long row = Math.max(0, Math.min(mapping.getRowCount() - 1, y / CELL_SIZE));
        return mapping.cellStart(row, 0);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        long offset = offsetAt(e.getPoint());
        if (offset < 0) {
            return null;
        }
        FileRegion region = model.innermostAt(offset);
        String text = "0x" + Long.toHexString(offset);
        return region == null ? text : text + " " + region.getPath();
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0, 0, getWidth(), getHeight());
        FileVisualization vis = model.getVisualization();
        ViewMapping mapping = mapping();
        if (vis == null || mapping == null) {
            return;
        }
        RenderRequest wanted = new RenderRequest(model.getGeneration(), mapping, model.getStyle());
        if (rendered == null || !rendered.request.sameAs(wanted) || rendered.isOutdated(vis.getStatistics())) {
            schedule(wanted, vis);
        }
        if (rendered != null && rendered.request.generation == wanted.generation) {
            g.drawImage(rendered.image, 0, 0, null);
        }
        if (showVisibleRange) {
            paintVisibleRange(g, mapping);
        }
    }

    private void paintVisibleRange(Graphics g, ViewMapping mapping) {
        long start = model.getVisibleStart();
        long end = model.getVisibleEnd();
        if (end <= start) {
            return;
        }
        long firstRow = mapping.rowOf(start);
        long lastRow = mapping.rowOf(Math.max(start, Math.min(end, mapping.getFileLength()) - 1));
        int y = (int) (firstRow * CELL_SIZE);
        int h = (int) Math.max(2, (lastRow - firstRow + 1) * CELL_SIZE);
        int w = TRACKS.size() * trackWidth + (TRACKS.size() - 1) * GAP;
        g.setColor(new Color(255, 255, 255, 50));
        g.fillRect(0, y, w, h);
        g.setColor(Color.white);
        g.drawRect(0, y, w - 1, h - 1);
    }

    private void schedule(RenderRequest request, FileVisualization vis) {
        if (pending != null && pending.sameAs(request)) {
            return; // already rendering this picture
        }
        pending = request;
        ByteStatistics statistics = vis.getStatistics();
        executor.execute(() -> {
            Rendered result = null;
            try {
                // read before rendering, the renderers hatch everything that is computed later
                long computed = statistics.getComputedLength();
                result = new Rendered(request, render(request, vis), computed,
                        statistics.isComplete() || request.mapping.getBytesPerCell() < statistics.getBlockSize());
            } catch (ByteSourceClosedException e) {
                // another file was loaded meanwhile
            } catch (Throwable t) {
                LOGGER.error("Could not render the overview: " + t);
            }
            Rendered done = result;
            SwingUtilities.invokeLater(() -> {
                if (pending == request) {
                    pending = null;
                }
                if (done != null && done.request.generation == model.getGeneration()) {
                    rendered = done;
                    repaint();
                }
            });
        });
    }

    private BufferedImage render(RenderRequest request, FileVisualization vis) {
        ViewMapping mapping = request.mapping;
        int rows = (int) mapping.getRowCount();
        Viewport viewport = new Viewport(mapping, 0, rows, CELL_SIZE);
        int width = TRACKS.size() * trackWidth + (TRACKS.size() - 1) * GAP;
        BufferedImage image = new BufferedImage(width, Math.max(1, rows * CELL_SIZE), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            int x = 0;
            for (Track track : TRACKS) {
                BufferedImage trackImage = RenderSupport.renderTrack(track, vis, viewport, CELL_SIZE, request.style);
                if (trackImage != null) {
                    g.drawImage(trackImage, x, 0, null);
                }
                x += trackWidth + GAP;
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static final class RenderRequest {
        final int generation;
        final ViewMapping mapping;
        final RenderStyle style;

        RenderRequest(int generation, ViewMapping mapping, RenderStyle style) {
            this.generation = generation;
            this.mapping = mapping;
            this.style = style;
        }

        boolean sameAs(RenderRequest other) {
            return generation == other.generation
                    && mapping.getBytesPerCell() == other.mapping.getBytesPerCell()
                    && mapping.getColumns() == other.mapping.getColumns()
                    && mapping.getFileLength() == other.mapping.getFileLength()
                    && style.equals(other.style);
        }
    }

    private static final class Rendered {
        final RenderRequest request;
        final BufferedImage image;
        final long computedLength;
        final boolean complete;

        Rendered(RenderRequest request, BufferedImage image, long computedLength, boolean complete) {
            this.request = request;
            this.image = image;
            this.computedLength = computedLength;
            this.complete = complete;
        }

        /** the picture was rendered while statistics were missing that are available now */
        boolean isOutdated(ByteStatistics statistics) {
            return !complete && statistics.getComputedLength() > computedLength;
        }
    }
}
