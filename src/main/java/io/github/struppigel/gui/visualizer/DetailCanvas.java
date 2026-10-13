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

import io.github.struppigel.gui.utils.HexFont;
import io.github.struppigel.gui.visualizer.ZoomLadder.ZoomLevel;
import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.visualizer.engine.ByteStatistics;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.engine.ViewMapping;
import io.github.struppigel.tools.visualizer.engine.Viewport;
import io.github.struppigel.tools.visualizer.render.RenderStyle;
import io.github.struppigel.tools.visualizer.render.Track;
import io.github.struppigel.tools.visualizer.render.VisualizationRenderer;
import io.github.struppigel.tools.visualizer.render.VisualizerColors;
import io.github.struppigel.tools.visualizer.engine.ByteSourceClosedException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.LongConsumer;

/**
 * The zoomable view of the file. Shows byte plot, entropy and structure of the visible rows next to each other,
 * at the deepest zoom level the bytes as hex and ASCII. The pictures are rendered in tiles in the background,
 * painting only draws the tiles.
 */
public class DetailCanvas extends JComponent {

    static final int TILE_ROWS = 256;
    static final int RULER_WIDTH = VisualizationRenderer.RULER_WIDTH;
    static final int GAP = VisualizationRenderer.TRACK_GAP;
    private static final Color BACKGROUND = new Color(30, 30, 30);
    private static final Color RULER_TEXT = new Color(170, 170, 170);
    private static final Color SELECTION_FILL = new Color(255, 255, 0, 60);
    private static final Color SELECTION_CELL = Color.yellow;
    private static final Color HOVER_CELL = Color.white;
    private static final int SCROLL_ROWS_PER_NOTCH = 3;
    /** the scroll bar works with int, rows of large files at fine zoom levels are scaled down */
    private static final long MAX_SCROLL_VALUE = 1_000_000_000L;

    private final VisualizationModel model;
    private final TileCache cache = new TileCache();
    private final TileScheduler scheduler;
    private final JScrollBar scrollBar = new JScrollBar(JScrollBar.VERTICAL);

    private List<ZoomLevel> ladder;
    private int ladderTrackWidth;
    private int ladderHeight;
    private int levelIndex = 0;
    private long firstRow = 0;
    private long scrollScale = 1;
    private boolean adjustingScrollBar = false;
    private boolean hexEnabled = true;
    private volatile ViewState viewState;

    private LongConsumer clickListener;
    private RegionNavigator navigator;
    private final Map<String, Font> textFonts = new HashMap<>();
    private static final String[] HEX_DIGITS = new String[256];

    static {
        for (int i = 0; i < 256; i++) {
            HEX_DIGITS[i] = String.format("%02X", i);
        }
    }

    private Point dragStart;
    private long dragStartRow;
    private long rangeAnchor = -1;

    public DetailCanvas(VisualizationModel model) {
        this(model, RenderSupport.RENDER_POOL);
    }

    DetailCanvas(VisualizationModel model, Executor executor) {
        this.model = model;
        this.scheduler = new TileScheduler(executor);
        setOpaque(true);
        setFocusable(true);
        setFont(HexFont.font(Font.PLAIN, 12));
        scrollBar.addAdjustmentListener(e -> {
            if (!adjustingScrollBar) {
                setFirstRow(e.getValue() * scrollScale);
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayout();
                repaint();
            }
        });
        model.addListener(new ModelListener());
        MouseHandler mouse = new MouseHandler();
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addMouseWheelListener(mouse);
    }

    /**
     * @return the scroll bar that scrolls this canvas, to be placed next to it
     */
    JScrollBar getScrollBar() {
        return scrollBar;
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        repaint();
    }

    /**
     * @param clickListener receives the offset when the user clicks into the canvas
     */
    void setClickListener(LongConsumer clickListener) {
        this.clickListener = clickListener;
    }

    void setRegionNavigator(RegionNavigator navigator) {
        this.navigator = navigator;
    }

    // --- layout ---

    /**
     * @return the colored tracks the user enabled, at least one
     */
    private List<Track> baseTracks() {
        List<Track> tracks = new ArrayList<>();
        if (model.isByteplotVisible()) tracks.add(Track.BYTEPLOT);
        if (model.isEntropyVisible()) tracks.add(Track.ENTROPY);
        if (model.isStructureVisible() || tracks.isEmpty()) tracks.add(Track.STRUCTURE);
        return tracks;
    }

    /**
     * Computes the zoom levels for the current size. Keeps the zoom and the position as far as possible.
     *
     * @return false if there is nothing to show
     */
    boolean relayout() {
        FileVisualization vis = model.getVisualization();
        if (vis == null || getWidth() <= 0 || getHeight() <= 0) {
            return false;
        }
        int n = baseTracks().size();
        int trackWidth = Math.max(16, (getWidth() - RULER_WIDTH - (n - 1) * GAP) / n);
        if (ladder == null || trackWidth != ladderTrackWidth || getHeight() != ladderHeight) {
            long top = ladder == null ? 0 : mapping().cellStart(firstRow, 0);
            ZoomLevel previous = ladder == null ? null : level();
            ladder = ZoomLadder.create(vis.getFileLength(), trackWidth, getHeight());
            ladderTrackWidth = trackWidth;
            ladderHeight = getHeight();
            levelIndex = previous == null ? 0 : ZoomLadder.closest(ladder, previous);
            firstRow = 0;
            setFirstRowUnchecked(mapping().rowOf(Math.max(0, Math.min(top, vis.getFileLength() - 1))));
            viewChanged();
        }
        return true;
    }

    ZoomLevel level() {
        return ladder.get(levelIndex);
    }

    List<ZoomLevel> getLadder() {
        return ladder == null ? Collections.<ZoomLevel>emptyList() : ladder;
    }

    int getLevelIndex() {
        return levelIndex;
    }

    long getFirstRow() {
        return firstRow;
    }

    long getBytesPerCell() {
        return ladder == null ? 1 : level().bytesPerCell;
    }

    ViewMapping mapping() {
        ZoomLevel level = level();
        return new ViewMapping(model.getVisualization().getFileLength(), 0, level.bytesPerCell, level.columns);
    }

    private int visibleRows() {
        return Math.max(1, (getHeight() + level().cellHeight - 1) / level().cellHeight);
    }

    Viewport viewport() {
        return new Viewport(mapping(), firstRow, visibleRows(), level().cellHeight);
    }

    /**
     * A track and where it is drawn.
     */
    static final class TrackLayout {
        final Track track;
        final int x;
        final int cellWidth;
        final int width;

        TrackLayout(Track track, int x, int cellWidth, int width) {
            this.track = track;
            this.x = x;
            this.cellWidth = cellWidth;
            this.width = width;
        }
    }

    List<TrackLayout> trackLayouts() {
        ZoomLevel level = level();
        List<Track> tracks = new ArrayList<>();
        if (level.hex) {
            // the bytes are more important; which side tracks fit does not depend on the switched on tracks, so
            // that switching one off never shows another
            List<Track> fitting = new ArrayList<>();
            for (Track side : new Track[]{Track.BYTEPLOT, Track.ENTROPY}) {
                fitting.add(side);
                if (width(fitting, level) + hexWidth(level) > getWidth()) {
                    break;
                }
                if (side == Track.BYTEPLOT ? model.isByteplotVisible() : model.isEntropyVisible()) {
                    tracks.add(side);
                }
            }
            tracks.add(Track.HEX);
            tracks.add(Track.ASCII);
        } else {
            tracks.addAll(baseTracks());
        }
        List<TrackLayout> layouts = new ArrayList<>();
        int x = RULER_WIDTH;
        for (Track track : tracks) {
            int cellWidth = VisualizationRenderer.cellWidthOf(track, level.cellWidth);
            int width = level.columns * cellWidth;
            layouts.add(new TrackLayout(track, x, cellWidth, width));
            x += width + GAP;
        }
        return layouts;
    }

    private static int width(List<Track> tracks, ZoomLevel level) {
        int width = RULER_WIDTH;
        for (Track track : tracks) {
            width += level.columns * VisualizationRenderer.cellWidthOf(track, level.cellWidth) + GAP;
        }
        return width;
    }

    private static int hexWidth(ZoomLevel level) {
        return level.columns * (VisualizationRenderer.cellWidthOf(Track.HEX, level.cellWidth)
                + VisualizationRenderer.cellWidthOf(Track.ASCII, level.cellWidth)) + GAP;
    }

    // --- coordinates ---

    /**
     * @return the file offset at the point, -1 if there is none
     */
    public long offsetAt(Point p) {
        if (!relayout()) {
            return -1;
        }
        Viewport viewport = viewport();
        for (TrackLayout t : trackLayouts()) {
            if (p.x >= t.x && p.x < t.x + t.width) {
                return viewport.offsetAt(p.x - t.x, p.y, t.cellWidth);
            }
        }
        return -1;
    }

    /**
     * @return the upper left corner of the cell of the offset in the track, null if the track is not shown
     */
    public Point pixelOf(long offset, Track track) {
        if (!relayout()) {
            return null;
        }
        Viewport viewport = viewport();
        for (TrackLayout t : trackLayouts()) {
            if (t.track == track) {
                Point p = viewport.pixelOf(offset, t.cellWidth);
                return new Point(t.x + p.x, p.y);
            }
        }
        return null;
    }

    // --- navigation ---

    private long maxFirstRow() {
        long fullyVisible = Math.max(1, getHeight() / level().cellHeight);
        return Math.max(0, mapping().getRowCount() - fullyVisible);
    }

    void setFirstRow(long row) {
        if (ladder == null) {
            return;
        }
        long clamped = Math.max(0, Math.min(maxFirstRow(), row));
        if (clamped != firstRow) {
            firstRow = clamped;
            viewChanged();
            repaint();
        }
    }

    /**
     * Scrolls so that the offset is visible.
     *
     * @param onlyIfInvisible keep the position if the offset is visible already, otherwise center it
     */
    public void scrollToOffset(long offset, boolean onlyIfInvisible) {
        if (!relayout() || offset < 0) {
            return;
        }
        if (onlyIfInvisible && viewport().isVisible(offset)
                && mapping().rowOf(offset) < firstRow + getHeight() / level().cellHeight) {
            return;
        }
        setFirstRow(mapping().rowOf(offset) - visibleRows() / 2);
    }

    /**
     * Zooms in (positive steps) or out (negative steps) and keeps the offset at the anchor point where it is.
     *
     * @param anchor the point to zoom around, null for the center
     */
    public void zoom(int steps, Point anchor) {
        if (!relayout()) {
            return;
        }
        int newIndex = Math.max(0, Math.min(ladder.size() - 1, levelIndex + steps));
        if (newIndex == levelIndex) {
            return;
        }
        int anchorY = anchor == null ? getHeight() / 2 : anchor.y;
        long anchorOffset = anchor == null ? -1 : offsetAt(anchor);
        if (anchorOffset < 0) {
            anchorOffset = mapping().cellStart(Math.min(mapping().getRowCount() - 1,
                    firstRow + anchorY / level().cellHeight), 0);
        }
        levelIndex = newIndex;
        firstRow = 0;
        setFirstRowUnchecked(mapping().rowOf(anchorOffset) - anchorY / level().cellHeight);
        viewChanged();
        repaint();
    }

    private void setFirstRowUnchecked(long row) {
        firstRow = Math.max(0, Math.min(maxFirstRow(), row));
    }

    /**
     * Zooms so that the region fills most of the view and selects it.
     */
    public void zoomToRegion(FileRegion region) {
        if (region == null || !relayout()) {
            return;
        }
        long start = region.getStart();
        long end = Math.max(start + 1, region.getEnd());
        int target = 0;
        for (int i = ladder.size() - 1; i >= 0; i--) {
            ZoomLevel level = ladder.get(i);
            long rows = (end - 1) / level.bytesPerRow() - start / level.bytesPerRow() + 1;
            long fit = (long) (Math.max(1, getHeight() / level.cellHeight) * 0.8);
            if (rows <= Math.max(1, fit)) {
                target = i;
                break;
            }
        }
        levelIndex = target;
        firstRow = 0;
        long regionRows = mapping().rowOf(end - 1) - mapping().rowOf(start) + 1;
        long margin = Math.max(0, (getHeight() / level().cellHeight - regionRows) / 2);
        setFirstRowUnchecked(mapping().rowOf(start) - margin);
        model.selectRegion(region, start);
        viewChanged();
        repaint();
    }

    /**
     * Zooms in to the hex values, centers the offset and selects the structure there.
     */
    public void zoomToOffset(long offset) {
        if (!relayout() || offset < 0 || offset >= model.getVisualization().getFileLength()) {
            return;
        }
        levelIndex = ladder.size() - 1;
        firstRow = 0;
        setFirstRowUnchecked(mapping().rowOf(offset) - visibleRows() / 2);
        model.select(offset);
        viewChanged();
        repaint();
    }

    /**
     * Shows the whole file.
     */
    public void zoomToFit() {
        if (relayout()) {
            levelIndex = 0;
            firstRow = 0;
            viewChanged();
            repaint();
        }
    }

    /**
     * Asks the user for an offset, then shows and selects it.
     */
    void goToOffset() {
        String input = JOptionPane.showInputDialog(this, "Go to offset (hex with 0x prefix, or decimal):",
                "Go to offset", JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return;
        }
        long offset = parseOffset(input);
        FileVisualization vis = model.getVisualization();
        if (offset < 0 || vis == null || offset >= vis.getFileLength()) {
            JOptionPane.showMessageDialog(this, "Not an offset of the file: " + input, "Go to offset",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        goTo(offset);
    }

    void goTo(long offset) {
        scrollToOffset(offset, false);
        model.select(offset);
        if (clickListener != null) {
            clickListener.accept(offset);
        }
    }

    /**
     * @return the offset given as hex with 0x prefix, as decimal, or as hex without prefix; -1 if invalid
     */
    static long parseOffset(String text) {
        String s = text.trim().toLowerCase().replace("_", "");
        try {
            if (s.startsWith("0x")) {
                return Long.parseLong(s.substring(2), 16);
            }
            if (s.endsWith("h")) {
                return Long.parseLong(s.substring(0, s.length() - 1), 16);
            }
            if (s.matches("[0-9]+")) {
                return Long.parseLong(s);
            }
            if (s.matches("[0-9a-f]+")) {
                return Long.parseLong(s, 16);
            }
        } catch (NumberFormatException e) {
            // invalid
        }
        return -1;
    }

    /**
     * Updates everything that depends on the visible part of the file.
     */
    private void viewChanged() {
        updateScrollBar();
        Viewport viewport = viewport();
        long lastRow = Math.min(mapping().getRowCount() - 1, firstRow + visibleRows() - 1);
        viewState = new ViewState(model.getGeneration(), level(), firstRow / TILE_ROWS,
                Math.max(0, lastRow) / TILE_ROWS);
        model.setVisibleRange(viewport.getStartOffset(), viewport.getEndOffset());
    }

    private void updateScrollBar() {
        long rows = mapping().getRowCount();
        scrollScale = Math.max(1, (rows + MAX_SCROLL_VALUE - 1) / MAX_SCROLL_VALUE);
        int extent = (int) Math.max(1, Math.min(rows, getHeight() / level().cellHeight) / scrollScale);
        int max = (int) Math.max(extent, rows / scrollScale);
        adjustingScrollBar = true;
        try {
            scrollBar.setValues((int) (firstRow / scrollScale), extent, 0, max);
            scrollBar.setBlockIncrement(Math.max(1, extent - 1));
            scrollBar.setUnitIncrement(1);
        } finally {
            adjustingScrollBar = false;
        }
    }

    // --- painting ---

    @Override
    protected void paintComponent(Graphics g) {
        paintView(g, false);
    }

    /**
     * Renders the visible part synchronously into an image, e.g., for tests without a paint cycle.
     */
    BufferedImage renderToImage() {
        BufferedImage image = new BufferedImage(Math.max(1, getWidth()), Math.max(1, getHeight()),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            paintView(g, true);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void paintView(Graphics g, boolean synchronous) {
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, getWidth(), getHeight());
        if (!relayout()) {
            g.setColor(RULER_TEXT);
            g.drawString(model.getPeData() == null ? "Drop file here" : "Nothing to show", 10, 20);
            return;
        }
        FileVisualization vis = model.getVisualization();
        ZoomLevel level = level();
        ViewMapping mapping = mapping();
        long lastRow = Math.min(mapping.getRowCount() - 1, firstRow + visibleRows() - 1);
        List<TrackLayout> layouts = trackLayouts();
        // the hex and ASCII tiles have no text, the digits are drawn on top, sharp on any screen
        Map<Track, Map<Long, TileCache.Tile>> textTiles = new EnumMap<>(Track.class);
        for (TrackLayout t : layouts) {
            RenderStyle style = styleOf(t.track);
            for (long tileIndex = firstRow / TILE_ROWS; tileIndex <= lastRow / TILE_ROWS; tileIndex++) {
                TileCache.Key key = new TileCache.Key(model.getGeneration(), t.track, level.columns,
                        level.bytesPerCell, t.cellWidth, level.cellHeight, style, tileIndex);
                TileCache.Tile tile = cache.get(key);
                if (tile == null || tile.isOutdated(vis.getStatistics())) {
                    if (synchronous) {
                        TileCache.Tile rendered = renderTile(key, vis, mapping, level, t, style);
                        if (rendered != null) {
                            cache.put(key, rendered);
                            tile = rendered;
                        }
                    } else {
                        requestTile(key, vis, mapping, level, t, style);
                    }
                }
                if (tile != null) {
                    int y = (int) ((tileIndex * TILE_ROWS - firstRow) * level.cellHeight);
                    g.drawImage(tile.image, t.x, y, null);
                    if (isTextTrack(t.track)) {
                        textTiles.computeIfAbsent(t.track, k -> new HashMap<>()).put(tileIndex, tile);
                    }
                }
            }
        }
        paintRuler(g, mapping, level, lastRow);
        paintSelectionFill(g, layouts, mapping, level, lastRow);
        paintHexText(g, layouts, mapping, level, lastRow, textTiles);
        paintCellOutlines(g, layouts, mapping, level, lastRow);
    }

    private static boolean isTextTrack(Track track) {
        return track == Track.HEX || track == Track.ASCII;
    }

    /**
     * @return the style of the track; hex and ASCII cells get the colors the user picked, but no text, which the
     * canvas draws itself
     */
    RenderStyle styleOf(Track track) {
        return isTextTrack(track) ? model.getHexStyle().withHexText(false) : model.getStyle();
    }

    /**
     * Draws the bytes as hex digits and characters on the hex and ASCII tiles. Drawn on the screen and not into the
     * tiles, so that the text uses the desktop's text rendering and the screen's resolution, like Swing's text.
     *
     * @param tiles the shown tiles of the hex and ASCII tracks by tile index, rows without tile get no text yet
     */
    private void paintHexText(Graphics g, List<TrackLayout> layouts, ViewMapping mapping, ZoomLevel level,
                              long lastRow, Map<Track, Map<Long, TileCache.Tile>> tiles) {
        if (!level.hex || tiles.isEmpty() || lastRow < firstRow) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        try {
            Object desktopHints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
            if (desktopHints instanceof Map) {
                g2.addRenderingHints((Map<?, ?>) desktopHints);
            } else {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            }
            long start = mapping.cellStart(firstRow, 0);
            long end = Math.min(mapping.getFileLength(), start + (lastRow - firstRow + 1) * mapping.getBytesPerRow());
            byte[] bytes = new byte[(int) Math.max(0, end - start)];
            int read = model.getVisualization().getSource().read(start, bytes, 0, bytes.length);
            for (TrackLayout t : layouts) {
                Map<Long, TileCache.Tile> trackTiles = tiles.get(t.track);
                if (trackTiles == null) {
                    continue;
                }
                boolean ascii = t.track == Track.ASCII;
                g2.setFont(textFont(g2, t.cellWidth, level.cellHeight, ascii));
                FontMetrics metrics = g2.getFontMetrics();
                for (int i = 0; i < read; i++) {
                    long row = mapping.rowOf(start + i);
                    TileCache.Tile tile = trackTiles.get(row / TILE_ROWS);
                    if (tile == null) {
                        continue;
                    }
                    int cellX = mapping.columnOf(start + i) * t.cellWidth;
                    // the top left pixel has the cell color, the grid is at the bottom and right
                    int background = tile.image.getRGB(cellX, (int) (row % TILE_ROWS) * level.cellHeight);
                    g2.setColor(new Color(VisualizerColors.textRgbFor(background), true));
                    int value = bytes[i] & 0xff;
                    String text = ascii ? (value >= 33 && value <= 126 ? String.valueOf((char) value) : ".")
                            : HEX_DIGITS[value];
                    int x = t.x + cellX + (t.cellWidth - 1 - metrics.stringWidth(text)) / 2;
                    int y = (int) ((row - firstRow) * level.cellHeight)
                            + (level.cellHeight - 1 - metrics.getHeight()) / 2 + metrics.getAscent();
                    g2.drawString(text, x, y);
                }
            }
        } catch (ByteSourceClosedException e) {
            // another file is being loaded
        } finally {
            g2.dispose();
        }
    }

    /**
     * @return the largest font of the hex preview that fits two hex digits, or one character, into the cell
     */
    private Font textFont(Graphics2D g, int cellWidth, int cellHeight, boolean ascii) {
        String key = cellWidth + "x" + cellHeight + (ascii ? "a" : "h");
        Font font = textFonts.get(key);
        if (font == null) {
            String sample = ascii ? "W" : "WW";
            int size = Math.max(6, cellHeight - 1);
            font = HexFont.font(Font.PLAIN, size);
            FontMetrics metrics = g.getFontMetrics(font);
            while (size > 6 && (metrics.stringWidth(sample) > cellWidth - 2 || metrics.getAscent() > cellHeight - 2)) {
                size--;
                font = HexFont.font(Font.PLAIN, size);
                metrics = g.getFontMetrics(font);
            }
            textFonts.put(key, font);
        }
        return font;
    }

    /**
     * Lets the hex cells take the colors of the track at the point, if it is byte plot, entropy or structure.
     */
    void pickHexBackground(Point p) {
        for (TrackLayout t : trackLayouts()) {
            if (p.x >= t.x && p.x < t.x + t.width) {
                if (t.track == Track.BYTEPLOT || t.track == Track.ENTROPY || t.track == Track.STRUCTURE) {
                    model.setHexBackground(t.track);
                }
                return;
            }
        }
    }

    private void requestTile(TileCache.Key key, FileVisualization vis, ViewMapping mapping, ZoomLevel level,
                             TrackLayout layout, RenderStyle style) {
        scheduler.request(key,
                () -> renderTile(key, vis, mapping, level, layout, style),
                DetailCanvas.this::isStillWanted,
                tile -> {
                    if (key.generation == model.getGeneration()) {
                        cache.put(key, tile);
                        repaint();
                    }
                });
    }

    /** called in background threads */
    private boolean isStillWanted(TileCache.Key key) {
        ViewState state = viewState;
        return state != null && state.generation == key.generation
                && state.level.bytesPerCell == key.bytesPerCell && state.level.columns == key.columns
                && state.level.cellHeight == key.cellHeight
                && key.tileIndex >= state.firstTile - 1 && key.tileIndex <= state.lastTile + 1;
    }

    /** called in background threads, uses only thread-safe objects of PortEx */
    private static TileCache.Tile renderTile(TileCache.Key key, FileVisualization vis, ViewMapping mapping,
                                             ZoomLevel level, TrackLayout layout, RenderStyle style) {
        ByteStatistics statistics = vis.getStatistics();
        // read before rendering, the renderers hatch whatever is computed later
        long computed = statistics.getComputedLength();
        long tileFirstRow = key.tileIndex * TILE_ROWS;
        int rows = (int) Math.min(TILE_ROWS, mapping.getRowCount() - tileFirstRow);
        if (rows <= 0) {
            return null;
        }
        Viewport viewport = new Viewport(mapping, tileFirstRow, rows, level.cellHeight);
        BufferedImage image = RenderSupport.renderTrack(layout.track, vis, viewport, layout.cellWidth, style);
        if (image == null) {
            return null;
        }
        boolean complete = !RenderSupport.usesStatistics(layout.track)
                || computed >= mapping.getFileLength()
                || mapping.getBytesPerCell() < statistics.getBlockSize()
                || viewport.getEndOffset() <= computed;
        return new TileCache.Tile(image, complete, computed, viewport.getStartOffset());
    }

    private void paintRuler(Graphics g, ViewMapping mapping, ZoomLevel level, long lastRow) {
        g.setColor(RULER_TEXT);
        g.setFont(getFont());
        FontMetrics metrics = g.getFontMetrics();
        // label every n-th row, n a power of two, so that the labels do not overlap
        int every = 1;
        while (every * level.cellHeight < metrics.getHeight() + 2) {
            every *= 2;
        }
        for (long row = firstRow - firstRow % every; row <= lastRow; row += every) {
            if (row < firstRow) {
                continue;
            }
            long offset = mapping.cellStart(row, 0);
            String text = hexEnabled ? String.format("%08X", offset) : String.valueOf(offset);
            int y = (int) ((row - firstRow) * level.cellHeight);
            g.drawString(text, 4, y + Math.min(level.cellHeight, metrics.getAscent()) + 1);
            g.drawLine(RULER_WIDTH - 6, y, RULER_WIDTH - 2, y);
        }
    }

    private void paintSelectionFill(Graphics g, List<TrackLayout> layouts, ViewMapping mapping, ZoomLevel level,
                                    long lastRow) {
        g.setColor(SELECTION_FILL);
        if (model.hasRangeSelection()) {
            paintRange(g, layouts, mapping, level, lastRow, model.getRangeStart(), model.getRangeEnd());
        } else if (model.getSelectedRegion() != null) {
            for (PhysicalLocation loc : model.getSelectedRegion().getLocations()) {
                paintRange(g, layouts, mapping, level, lastRow, loc.from(), loc.from() + loc.size());
            }
        }
    }

    private void paintCellOutlines(Graphics g, List<TrackLayout> layouts, ViewMapping mapping, ZoomLevel level,
                                   long lastRow) {
        paintCell(g, layouts, mapping, level, lastRow, model.getSelectedOffset(), SELECTION_CELL);
        paintCell(g, layouts, mapping, level, lastRow, model.getHoverOffset(), HOVER_CELL);
    }

    private void paintRange(Graphics g, List<TrackLayout> layouts, ViewMapping mapping, ZoomLevel level,
                            long lastRow, long start, long end) {
        if (end <= start || start >= mapping.getFileLength()) {
            return;
        }
        end = Math.min(end, mapping.getFileLength());
        long startRow = mapping.rowOf(start);
        long endRow = mapping.rowOf(end - 1);
        for (long row = Math.max(firstRow, startRow); row <= Math.min(lastRow, endRow); row++) {
            int firstColumn = row == startRow ? mapping.columnOf(start) : 0;
            int lastColumn = row == endRow ? mapping.columnOf(end - 1) : level.columns - 1;
            int y = (int) ((row - firstRow) * level.cellHeight);
            for (TrackLayout t : layouts) {
                g.fillRect(t.x + firstColumn * t.cellWidth, y, (lastColumn - firstColumn + 1) * t.cellWidth,
                        level.cellHeight);
            }
        }
    }

    private void paintCell(Graphics g, List<TrackLayout> layouts, ViewMapping mapping, ZoomLevel level, long lastRow,
                           long offset, Color color) {
        if (offset < 0 || offset >= mapping.getFileLength()) {
            return;
        }
        long row = mapping.rowOf(offset);
        if (row < firstRow || row > lastRow) {
            return;
        }
        int y = (int) ((row - firstRow) * level.cellHeight);
        int column = mapping.columnOf(offset);
        g.setColor(color);
        for (TrackLayout t : layouts) {
            g.drawRect(t.x + column * t.cellWidth, y, Math.max(1, t.cellWidth - 1), Math.max(1, level.cellHeight - 1));
        }
    }

    // --- events ---

    private final class ModelListener implements VisualizationModel.Listener {
        @Override
        public void dataChanged() {
            cache.clear();
            scheduler.clear();
            ladder = null;
            levelIndex = 0;
            firstRow = 0;
            viewState = null;
            if (relayout()) {
                viewChanged();
            }
            repaint();
        }

        @Override
        public void statisticsProgressed() {
            repaint();
        }

        @Override
        public void selectionChanged() {
            repaint();
        }

        @Override
        public void hoverChanged() {
            repaint();
        }

        @Override
        public void styleChanged() {
            ladderTrackWidth = -1; // the tracks may change, relayout keeps zoom and position
            if (relayout()) {
                viewChanged();
            }
            repaint();
        }

        @Override
        public void viewRequested(long offset, boolean onlyIfInvisible) {
            scrollToOffset(offset, onlyIfInvisible);
        }
    }

    private final class MouseHandler extends MouseAdapter {
        private boolean dragged;

        @Override
        public void mouseMoved(MouseEvent e) {
            model.setHoverOffset(offsetAt(e.getPoint()));
        }

        @Override
        public void mouseExited(MouseEvent e) {
            model.setHoverOffset(-1);
        }

        @Override
        public void mousePressed(MouseEvent e) {
            requestFocusInWindow();
            if (e.isPopupTrigger()) {
                showPopup(e);
                return;
            }
            dragged = false;
            dragStart = e.getPoint();
            dragStartRow = firstRow;
            rangeAnchor = e.isShiftDown() ? offsetAt(e.getPoint()) : -1;
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                showPopup(e);
            }
            dragStart = null;
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (dragStart == null || ladder == null) {
                return;
            }
            dragged = true;
            if (rangeAnchor >= 0) {
                long offset = offsetAt(e.getPoint());
                if (offset >= 0) {
                    long bytesPerCell = level().bytesPerCell;
                    long from = Math.min(rangeAnchor, offset);
                    long to = Math.min(model.getVisualization().getFileLength(),
                            Math.max(rangeAnchor, offset) + bytesPerCell);
                    model.selectRange(from, to);
                }
            } else {
                long rows = (dragStart.y - e.getY()) / level().cellHeight;
                setFirstRow(dragStartRow + rows);
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (dragged || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            long offset = offsetAt(e.getPoint());
            if (offset < 0) {
                return;
            }
            if (e.getClickCount() == 2) {
                pickHexBackground(e.getPoint());
                zoomToRegion(model.innermostAt(offset));
            } else if (!e.isShiftDown()) {
                model.select(offset);
                if (clickListener != null) {
                    clickListener.accept(offset);
                }
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e) {
            if (ladder == null) {
                return;
            }
            if (e.isControlDown()) {
                if (e.getWheelRotation() < 0) {
                    pickHexBackground(e.getPoint()); // zooming in where the user points
                }
                zoom(-e.getWheelRotation(), e.getPoint());
            } else {
                setFirstRow(firstRow + (long) e.getWheelRotation() * SCROLL_ROWS_PER_NOTCH);
            }
        }

        private void showPopup(MouseEvent e) {
            long offset = offsetAt(e.getPoint());
            FileRegion region = model.innermostAt(offset);
            if (offset < 0) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            if (region != null) {
                JMenuItem zoomItem = new JMenuItem("Zoom to " + region.getName());
                zoomItem.addActionListener(a -> zoomToRegion(region));
                menu.add(zoomItem);
                if (navigator != null) {
                    JMenuItem treeItem = new JMenuItem("Show in tree");
                    treeItem.addActionListener(a -> navigator.showInTree(region, offset));
                    menu.add(treeItem);
                }
            }
            JMenuItem copyItem = new JMenuItem("Copy offset");
            copyItem.addActionListener(a -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(
                    new java.awt.datatransfer.StringSelection("0x" + Long.toHexString(offset)), null));
            menu.add(copyItem);
            menu.show(DetailCanvas.this, e.getX(), e.getY());
        }
    }

    /**
     * What the canvas shows, read by the background threads to skip tiles that are not needed anymore.
     */
    private static final class ViewState {
        final int generation;
        final ZoomLevel level;
        final long firstTile;
        final long lastTile;

        ViewState(int generation, ZoomLevel level, long firstTile, long lastTile) {
            this.generation = generation;
            this.level = level;
            this.firstTile = firstTile;
            this.lastTile = lastTile;
        }
    }
}
