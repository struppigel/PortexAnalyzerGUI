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

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.utils.HexFont;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.PaintMode;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.visualizer.ColorableItem;
import io.github.struppigel.tools.visualizer.VisualizerBuilder;
import io.github.struppigel.tools.visualizer.engine.ByteStatistics;
import io.github.struppigel.tools.visualizer.engine.CachedByteSource;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.engine.ProgressListener;
import io.github.struppigel.tools.visualizer.render.RegionColorScheme;
import io.github.struppigel.tools.visualizer.render.RenderStyle;
import io.github.struppigel.tools.visualizer.render.Track;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * State of the visualization of one file that is shared by all visualizer views: the PortEx visualization, the
 * selection, the hovered offset and the display options. Must only be used in the event dispatch thread.
 */
public class VisualizationModel {

    private static final Logger LOGGER = LogManager.getLogger();

    /**
     * Receives the changes of the model. All methods are called in the event dispatch thread.
     */
    public interface Listener {
        /** a file was loaded or unloaded, everything must be redrawn */
        default void dataChanged() {}

        /** more byte statistics are available */
        default void statisticsProgressed() {}

        default void selectionChanged() {}

        default void hoverChanged() {}

        /** the painted structures or the visible tracks changed */
        default void styleChanged() {}

        /**
         * the views shall show the offset, e.g., because the user navigated to it
         *
         * @param onlyIfInvisible true to keep the view as it is if the offset is visible already, false to center it
         */
        default void viewRequested(long offset, boolean onlyIfInvisible) {}

        /** the range of the file that the detail view shows changed */
        default void visibleRangeChanged() {}

        /** a listing of structures was set or cleared, see {@link VisualizationModel#showRegions} */
        default void listedRegionsChanged() {}
    }

    private final List<Listener> listeners = new ArrayList<>();

    private FullPEData peData;
    private FileVisualization visualization;
    private CachedByteSource source;
    private VisualizationLoadWorker statisticsWorker;
    private int generation;

    private long selectedOffset = -1;
    private FileRegion selectedRegion;
    private long rangeStart = -1;
    private long rangeEnd = -1;
    private long hoverOffset = -1;
    private long visibleStart = 0;
    private long visibleEnd = 0;
    private boolean revealSuppressed = false;
    // the structures that the structures view lists instead of the ones at the pointer, null title if none
    private String listedTitle;
    private List<FileRegion> listedRegions = Collections.emptyList();

    private RenderStyle style = RenderStyle.defaults();
    private Track hexBackground = Track.STRUCTURE;
    private boolean markersVisible = true;
    private boolean byteplotVisible = true;
    private boolean entropyVisible = true;
    private boolean structureVisible = true;

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * Shows the file and starts to compute its byte statistics in the background.
     *
     * @param data the loaded file, null to show nothing
     */
    public void load(FullPEData data) {
        load(data, true);
    }

    /**
     * Like {@link #load(FullPEData)}, but computes the statistics in the calling thread. For tests.
     */
    void loadSynchronously(FullPEData data) {
        load(data, false);
    }

    private void load(FullPEData data, boolean inBackground) {
        dispose();
        generation++;
        this.peData = data;
        resetSelection();
        listedTitle = null;
        listedRegions = Collections.emptyList();
        if (data != null) {
            visualization = createVisualization(data);
        }
        fireDataChanged();
        if (visualization != null) {
            if (inBackground) {
                statisticsWorker = new VisualizationLoadWorker(this, visualization.getStatistics(), generation);
                WorkerKiller.getInstance().addWorker(statisticsWorker);
                statisticsWorker.execute();
            } else {
                try {
                    visualization.getStatistics().compute(ProgressListener.NONE);
                } catch (IOException e) {
                    LOGGER.error("Could not compute the byte statistics", e);
                }
                statisticsProgressed(generation);
            }
        }
    }

    private FileVisualization createVisualization(FullPEData data) {
        File file = data.getFile();
        StructureMap map = data.getStructureMap();
        if (map == null) {
            map = StructureMap.empty(file.length());
        }
        try {
            ByteStatistics statistics = new ByteStatistics(file);
            source = new CachedByteSource(file);
            RegionColorScheme scheme = RegionColorScheme.of(map, colors(), RegionColorScheme.Mode.DEFAULT);
            return new FileVisualization(map, statistics, source, scheme);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Could not visualize " + file, e);
            closeSource();
            return null;
        }
    }

    /**
     * the default colors of PortEx, but with the cyan entropy that the PortexAnalyzer GUI always had
     */
    private static Map<ColorableItem, Color> colors() {
        Map<ColorableItem, Color> colors = new EnumMap<>(ColorableItem.class);
        colors.putAll(VisualizerBuilder.getDefaultColors());
        colors.put(ColorableItem.ENTROPY, Color.cyan);
        return colors;
    }

    /**
     * Stops computing statistics and releases the file. The model shows nothing afterwards.
     */
    public void dispose() {
        if (statisticsWorker != null) {
            statisticsWorker.cancel(true);
            statisticsWorker = null;
        }
        closeSource();
        visualization = null;
    }

    private void closeSource() {
        if (source != null) {
            try {
                source.close();
            } catch (IOException e) {
                LOGGER.warn("Could not close " + source.getFile() + ": " + e.getMessage());
            }
            source = null;
        }
    }

    void statisticsProgressed(int forGeneration) {
        if (forGeneration != generation) {
            return;
        }
        for (Listener l : new ArrayList<>(listeners)) {
            l.statisticsProgressed();
        }
    }

    /**
     * @return the visualization of the current file, null if no file is loaded or it cannot be visualized
     */
    public FileVisualization getVisualization() {
        return visualization;
    }

    public FullPEData getPeData() {
        return peData;
    }

    /**
     * @return a number that changes whenever another file is loaded
     */
    public int getGeneration() {
        return generation;
    }

    // --- selection ---

    /**
     * Selects the innermost structure at the offset.
     */
    public void select(long offset) {
        selectRegion(innermostAt(offset), offset);
    }

    /**
     * @param region the selected structure, may be null
     * @param offset the selected offset
     */
    public void selectRegion(FileRegion region, long offset) {
        selectedOffset = offset;
        selectedRegion = region;
        rangeStart = -1;
        rangeEnd = -1;
        fireSelectionChanged();
    }

    /**
     * Marks the structure that the user picked in a list, and asks the views to show it if they do not.
     *
     * @param offset the offset that the other views describe, so that they keep their content
     */
    public void showRegion(FileRegion region, long offset) {
        selectRegion(region, offset);
        fireViewRequested(region.getStart(), true);
    }

    /**
     * Selects a range of bytes.
     */
    public void selectRange(long from, long toExclusive) {
        selectedOffset = from;
        selectedRegion = null;
        rangeStart = from;
        rangeEnd = toExclusive;
        fireSelectionChanged();
    }

    public void clearSelection() {
        resetSelection();
        fireSelectionChanged();
    }

    private void resetSelection() {
        selectedOffset = -1;
        selectedRegion = null;
        rangeStart = -1;
        rangeEnd = -1;
        hoverOffset = -1;
    }

    /**
     * Selects the structure that starts at the offset and asks the views to show it. Used when the user navigates to
     * an offset elsewhere, e.g., in the tree or in a table.
     */
    public void reveal(long offset) {
        if (revealSuppressed || visualization == null) {
            return;
        }
        selectRegion(revealedRegion(visualization.getStructureMap(), offset), offset);
        fireViewRequested(offset, true);
    }

    /**
     * Runs the action without revealing offsets, e.g., while the tree shows a structure that the user picked in the
     * visualizer, which must not overwrite the selection.
     */
    public void withoutReveal(Runnable action) {
        boolean previous = revealSuppressed;
        revealSuppressed = true;
        try {
            action.run();
        } finally {
            revealSuppressed = previous;
        }
    }

    /**
     * The structure to select for an offset that a table or the tree navigates to. That is the most specific
     * structure that starts at the offset. Header fields and table entries only count if they do not start at the
     * same offset as their structure, so that the COFF File Header node selects the header, not its first field.
     */
    static FileRegion revealedRegion(StructureMap map, long offset) {
        Optional<FileRegion> best = map.bestRegionStartingAt(offset);
        FileRegion region = best.isPresent() ? best.get() : map.innermostAt(offset).orElse(null);
        while (region != null && region.getType().getPaintMode() == PaintMode.NONE
                && region.getType() != RegionType.IMPORT_FUNCTION) {
            Optional<FileRegion> parent = region.getParent();
            if (!parent.isPresent() || parent.get().getStart() != region.getStart()
                    || parent.get().getType() == RegionType.HEADERS) {
                break;
            }
            region = parent.get();
        }
        return region;
    }

    public FileRegion innermostAt(long offset) {
        if (visualization == null || offset < 0) {
            return null;
        }
        return visualization.getStructureMap().innermostAt(offset).orElse(null);
    }

    public long getSelectedOffset() {
        return selectedOffset;
    }

    /**
     * @return the selected structure, null if none
     */
    public FileRegion getSelectedRegion() {
        return selectedRegion;
    }

    public boolean hasRangeSelection() {
        return rangeStart >= 0 && rangeEnd > rangeStart;
    }

    public long getRangeStart() {
        return rangeStart;
    }

    public long getRangeEnd() {
        return rangeEnd;
    }

    // --- hover ---

    public void setHoverOffset(long offset) {
        if (offset != hoverOffset) {
            hoverOffset = offset;
            for (Listener l : new ArrayList<>(listeners)) {
                l.hoverChanged();
            }
        }
    }

    /**
     * @return the offset under the mouse in a visualizer view, -1 if none
     */
    public long getHoverOffset() {
        return hoverOffset;
    }

    // --- views ---

    /**
     * Asks the views to center the offset, e.g., when the user clicks into the overview.
     */
    public void requestView(long offset) {
        fireViewRequested(offset, false);
    }

    /**
     * Set by the detail view, so that the overview can show which part of the file is visible.
     */
    public void setVisibleRange(long start, long endExclusive) {
        if (start != visibleStart || endExclusive != visibleEnd) {
            visibleStart = start;
            visibleEnd = endExclusive;
            for (Listener l : new ArrayList<>(listeners)) {
                l.visibleRangeChanged();
            }
        }
    }

    public long getVisibleStart() {
        return visibleStart;
    }

    public long getVisibleEnd() {
        return visibleEnd;
    }

    // --- style ---

    public RenderStyle getStyle() {
        return style;
    }

    /**
     * @return the style for the hex and ASCII tracks: the cells have the colors of {@link #getHexBackground()} and
     * the digits the font of the hex preview
     */
    public RenderStyle getHexStyle() {
        return style.withHexBackground(hexBackground).withHexFontFamily(HexFont.family());
    }

    /**
     * @param track whose colors the hex and ASCII cells get: structure, byte plot or entropy
     */
    public void setHexBackground(Track track) {
        if (track != Track.STRUCTURE && track != Track.BYTEPLOT && track != Track.ENTROPY) {
            throw new IllegalArgumentException("no colors for hex cells: " + track);
        }
        if (track != hexBackground) {
            hexBackground = track;
            fireStyleChanged();
        }
    }

    public Track getHexBackground() {
        return hexBackground;
    }

    /**
     * Shows or hides the markers of anomalies and signature matches.
     */
    public void setMarkersVisible(boolean visible) {
        markersVisible = visible;
        style = style.withPainted(RegionType.ANOMALY, visible).withPainted(RegionType.SIGNATURE_MATCH, visible);
        fireStyleChanged();
    }

    public boolean isMarkersVisible() {
        return markersVisible;
    }

    public void setByteplotVisible(boolean visible) {
        byteplotVisible = visible;
        fireStyleChanged();
    }

    public boolean isByteplotVisible() {
        return byteplotVisible;
    }

    public void setEntropyVisible(boolean visible) {
        entropyVisible = visible;
        fireStyleChanged();
    }

    public boolean isEntropyVisible() {
        return entropyVisible;
    }

    public void setStructureVisible(boolean visible) {
        structureVisible = visible;
        fireStyleChanged();
    }

    public boolean isStructureVisible() {
        return structureVisible;
    }

    // --- listing of structures ---

    /**
     * Makes the structures view list the structures, e.g. all of a type, instead of the ones at the pointer.
     *
     * @param title what the structures have in common
     * @param regions the structures, they are shown by offset
     */
    public void showRegions(String title, List<FileRegion> regions) {
        List<FileRegion> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparingLong(FileRegion::getStart).thenComparingInt(FileRegion::getDepth));
        listedTitle = title;
        listedRegions = Collections.unmodifiableList(sorted);
        fireListedRegionsChanged();
    }

    /**
     * Makes the structures view show the structures at the pointer again.
     */
    public void clearListedRegions() {
        if (listedTitle != null) {
            listedTitle = null;
            listedRegions = Collections.emptyList();
            fireListedRegionsChanged();
        }
    }

    public boolean hasListedRegions() {
        return listedTitle != null;
    }

    /**
     * @return what the listed structures have in common, null if no structures are listed
     */
    public String getListedTitle() {
        return listedTitle;
    }

    public List<FileRegion> getListedRegions() {
        return listedRegions;
    }

    // --- events ---

    private void fireListedRegionsChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.listedRegionsChanged();
        }
    }

    private void fireDataChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.dataChanged();
        }
    }

    private void fireSelectionChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.selectionChanged();
        }
    }

    private void fireStyleChanged() {
        for (Listener l : new ArrayList<>(listeners)) {
            l.styleChanged();
        }
    }

    private void fireViewRequested(long offset, boolean onlyIfInvisible) {
        for (Listener l : new ArrayList<>(listeners)) {
            l.viewRequested(offset, onlyIfInvisible);
        }
    }
}
