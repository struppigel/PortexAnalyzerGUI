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

import io.github.struppigel.tools.ShannonEntropy;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.visualizer.engine.ByteClass;
import io.github.struppigel.tools.visualizer.engine.ByteSource;
import io.github.struppigel.tools.visualizer.engine.ByteSourceClosedException;
import io.github.struppigel.tools.visualizer.engine.ByteStatistics;
import io.github.struppigel.tools.visualizer.engine.ByteStats;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.engine.LocalEntropy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Describes the offset under the mouse, or the selected one: the byte, the entropy and all structures there.
 */
public class InfoPane extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int UPDATE_DELAY_MS = 33;
    private static final int SHOWN_CELL_BYTES = 16;
    private static final int SHOWN_BYTES_PER_ROW = 8;
    private static final String NL = "\n";

    private final VisualizationModel model;
    private final LongSupplier bytesPerCell;
    private final InfoGrid grid = new InfoGrid();
    private final DefaultListModel<FileRegion> regionListModel = new DefaultListModel<>();
    private final JList<FileRegion> regionList = new JList<FileRegion>(regionListModel) {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true; // as wide as the visible area, the cells wrap their text
        }
    };
    private final Timer updateTimer;
    private final ByteStats stats = new ByteStats();
    private boolean hexEnabled = true;
    private Consumer<FileRegion> zoomAction;
    private RegionNavigator navigator;

    private long shownOffset = Long.MIN_VALUE;
    private long rangeEntropyStart = -1;
    private long rangeEntropyEnd = -1;
    private Double rangeEntropy;
    private SwingWorker<Double, Void> rangeWorker;

    /**
     * @param bytesPerCell the bytes per cell of the detail view
     */
    public InfoPane(VisualizationModel model, LongSupplier bytesPerCell) {
        super(new BorderLayout());
        this.model = model;
        this.bytesPerCell = bytesPerCell;
        regionList.setCellRenderer(new RegionRenderer());
        regionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        regionList.addMouseListener(new RegionMouseHandler());
        JLabel title = new JLabel("Structures, innermost first");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        JPanel titlePanel = new JPanel(new BorderLayout(0, Spacing.GAP / 2));
        titlePanel.setBorder(Spacing.padding(Spacing.GAP, Spacing.PAD, 0, Spacing.PAD));
        titlePanel.add(title, BorderLayout.NORTH);
        titlePanel.add(new JSeparator(), BorderLayout.SOUTH);
        JPanel regionPanel = new JPanel(new BorderLayout());
        regionPanel.add(titlePanel, BorderLayout.NORTH);
        // flat on the color of the panel, like the grid above and the legend below
        regionList.setBackground(regionPanel.getBackground());
        JScrollPane regionScroll = new JScrollPane(regionList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        regionScroll.setBorder(null);
        regionScroll.getViewport().setBackground(regionPanel.getBackground());
        regionPanel.add(regionScroll, BorderLayout.CENTER);
        // the cells wrap at the width of the list, they must be measured again when it changes
        regionList.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                regionList.setFixedCellHeight(10);
                regionList.setFixedCellHeight(-1);
            }
        });
        add(grid, BorderLayout.NORTH);
        add(regionPanel, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 400));

        updateTimer = new Timer(UPDATE_DELAY_MS, e -> updateNow());
        updateTimer.setRepeats(false);
        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void dataChanged() {
                shownOffset = Long.MIN_VALUE;
                rangeEntropyStart = -1;
                rangeEntropy = null;
                scheduleUpdate();
            }

            @Override
            public void hoverChanged() {
                scheduleUpdate();
            }

            @Override
            public void selectionChanged() {
                scheduleUpdate();
            }

            @Override
            public void statisticsProgressed() {
                scheduleUpdate();
            }
        });
        updateNow();
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        shownOffset = Long.MIN_VALUE;
        updateNow();
    }

    void setZoomAction(Consumer<FileRegion> zoomAction) {
        this.zoomAction = zoomAction;
    }

    void setRegionNavigator(RegionNavigator navigator) {
        this.navigator = navigator;
    }

    private void scheduleUpdate() {
        if (!updateTimer.isRunning()) {
            updateTimer.start();
        }
    }

    /**
     * @return the offset that is described: the hovered one, otherwise the selected one, -1 if none
     */
    private long describedOffset() {
        return model.getHoverOffset() >= 0 ? model.getHoverOffset() : model.getSelectedOffset();
    }

    void updateNow() {
        FileVisualization vis = model.getVisualization();
        long offset = describedOffset();
        List<String[]> rows = new ArrayList<>();
        if (vis == null) {
            rows.add(new String[]{null, "No file loaded"});
        } else if (offset < 0 || offset >= vis.getFileLength()) {
            rows.add(new String[]{null, "Point at the visualization to see the structures and bytes there."});
            rows.add(new String[]{null, "Ctrl + wheel zooms, Ctrl + G goes to an offset."});
        } else {
            describeOffset(rows, vis, offset);
        }
        if (vis != null && model.hasRangeSelection()) {
            describeRange(rows, vis.getStructureMap().getFileLength());
        }
        grid.setRows(rows);
        updateRegions(vis, offset);
    }

    private static String[] mono(String label, String value) {
        return new String[]{label, value, "mono"};
    }

    private void describeOffset(List<String[]> rows, FileVisualization vis, long offset) {
        ByteSource source = vis.getSource();
        long cellSize = Math.max(1, bytesPerCell.getAsLong());
        long cellStart = offset - offset % cellSize;
        long cellEnd = Math.min(vis.getFileLength(), cellStart + cellSize);
        String kind = model.getHoverOffset() >= 0 ? "" : "  (selected)";
        rows.add(mono("Offset", format(offset) + kind));
        try {
            int value = source.byteAt(offset);
            if (value >= 0) {
                rows.add(mono("Byte", String.format("%02X  %3d  '%s'", value, value, printable(value))));
            }
            if (cellSize > 1) {
                rows.add(mono("Cell", format(cellStart) + " - " + format(cellEnd - 1)
                        + " (" + (cellEnd - cellStart) + " bytes)"));
                byte[] bytes = new byte[(int) Math.min(SHOWN_CELL_BYTES, cellEnd - cellStart)];
                int read = source.read(cellStart, bytes, 0, bytes.length);
                for (int from = 0; from < read; from += SHOWN_BYTES_PER_ROW) {
                    rows.add(mono("", hexBytes(bytes, from, Math.min(read, from + SHOWN_BYTES_PER_ROW))));
                }
            }
            describeStatistics(rows, vis, cellStart, cellEnd);
        } catch (ByteSourceClosedException e) {
            // another file is being loaded
        } catch (RuntimeException e) {
            LOGGER.warn("Could not describe offset " + offset + ": " + e.getMessage());
        }
    }

    private void describeStatistics(List<String[]> rows, FileVisualization vis, long cellStart, long cellEnd) {
        ByteStatistics statistics = vis.getStatistics();
        long cellSize = cellEnd - cellStart;
        double mean;
        double max;
        double[] fractions = new double[ByteClass.values().length];
        if (cellSize >= statistics.getBlockSize()) {
            if (cellEnd > statistics.getComputedLength()) {
                rows.add(new String[]{"Entropy", "computing ..."});
                return;
            }
            statistics.aggregate(cellStart, cellEnd, stats);
            mean = stats.getMeanEntropy();
            max = stats.getMaxEntropy();
            for (ByteClass c : ByteClass.values()) {
                fractions[c.ordinal()] = stats.getFraction(c);
            }
        } else {
            double[] meanOut = new double[1];
            double[] maxOut = new double[1];
            LocalEntropy.compute(vis.getSource(), cellStart, (int) cellSize, 1, meanOut, maxOut);
            mean = meanOut[0];
            max = maxOut[0];
            byte[] bytes = new byte[(int) cellSize];
            int read = vis.getSource().read(cellStart, bytes, 0, bytes.length);
            for (int i = 0; i < read; i++) {
                fractions[ByteClass.of(bytes[i] & 0xff).ordinal()] += 1.0 / read;
            }
        }
        if (!Double.isNaN(mean)) {
            rows.add(new String[]{"Entropy", String.format("%.2f mean, %.2f max bits per byte", mean * 8, max * 8)});
        }
        rows.add(new String[]{"Bytes", String.format("zero %.0f%%, 0xFF %.0f%%",
                100 * fractions[ByteClass.ZERO.ordinal()], 100 * fractions[ByteClass.MAX.ordinal()])});
        rows.add(new String[]{"", String.format("text %.0f%%, control %.0f%%, high %.0f%%",
                100 * fractions[ByteClass.PRINTABLE.ordinal()], 100 * fractions[ByteClass.CONTROL.ordinal()],
                100 * fractions[ByteClass.HIGH.ordinal()])});
    }

    private void describeRange(List<String[]> rows, long fileLength) {
        long start = model.getRangeStart();
        long end = Math.min(fileLength, model.getRangeEnd());
        rows.add(mono("Range", format(start) + " - " + format(end - 1) + " (" + (end - start) + " bytes)"));
        if (start == rangeEntropyStart && end == rangeEntropyEnd && rangeEntropy != null) {
            rows.add(new String[]{"Entropy", String.format("%.2f bits per byte", rangeEntropy * 8)});
        } else {
            rows.add(new String[]{"Entropy", "computing ..."});
            computeRangeEntropy(model.getPeData().getFile(), start, end);
        }
    }
    private void computeRangeEntropy(File file, long start, long end) {
        if (rangeWorker != null && start == rangeEntropyStart && end == rangeEntropyEnd) {
            return; // already computing
        }
        if (rangeWorker != null) {
            rangeWorker.cancel(true);
        }
        rangeEntropyStart = start;
        rangeEntropyEnd = end;
        rangeEntropy = null;
        rangeWorker = new SwingWorker<Double, Void>() {
            @Override
            protected Double doInBackground() {
                return ShannonEntropy.entropy(file, start, end - start);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    rangeEntropy = get();
                    rangeWorker = null;
                    updateNow();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.warn("Could not compute the entropy of the range: " + e.getMessage());
                }
            }
        };
        rangeWorker.execute();
    }

    private void updateRegions(FileVisualization vis, long offset) {
        if (offset == shownOffset) {
            return;
        }
        shownOffset = offset;
        regionListModel.clear();
        if (vis == null || offset < 0) {
            return;
        }
        List<FileRegion> regions = vis.getStructureMap().regionsAt(offset);
        for (FileRegion region : regions) {
            regionListModel.addElement(region);
        }
    }

    /**
     * @return the shown text and the names of the shown structures, for tests
     */
    String getInfoText() {
        StringBuilder text = new StringBuilder(grid.getText());
        for (int i = 0; i < regionListModel.size(); i++) {
            text.append(NL).append(regionListModel.get(i).getName());
        }
        return text.toString();
    }

    /**
     * @return the list of the structures at the described offset, for tests
     */
    JList<FileRegion> getRegionList() {
        return regionList;
    }

    private String format(long value) {
        return hexEnabled ? "0x" + Long.toHexString(value) : String.valueOf(value);
    }

    private static String printable(int value) {
        return value >= 0x20 && value < 0x7f ? String.valueOf((char) value) : ".";
    }

    private static String hexBytes(byte[] bytes, int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString().trim();
    }

    /**
     * A structure as color swatch, name and details. The texts wrap at the width of the list, also within long
     * words, so that nothing is cut off.
     */
    private final class RegionRenderer extends JPanel implements ListCellRenderer<FileRegion> {
        private static final int GAP = 6;
        private final JLabel swatch = new JLabel();
        private final JTextArea name = wrappingText(Font.BOLD);
        private final JTextArea details = wrappingText(Font.PLAIN);

        RegionRenderer() {
            super(new BorderLayout(GAP, 0));
            setBorder(Spacing.padding(Spacing.GAP / 2 + 1, Spacing.PAD, Spacing.GAP / 2 + 1, Spacing.PAD));
            swatch.setVerticalAlignment(SwingConstants.TOP);
            swatch.setBorder(BorderFactory.createEmptyBorder(2, 0, 0, 0));
            JPanel texts = new JPanel(new BorderLayout());
            texts.setOpaque(false);
            texts.add(name, BorderLayout.NORTH);
            texts.add(details, BorderLayout.CENTER);
            add(swatch, BorderLayout.WEST);
            add(texts, BorderLayout.CENTER);
        }

        private JTextArea wrappingText(int style) {
            JTextArea text = new JTextArea();
            text.setLineWrap(true);
            text.setWrapStyleWord(true);
            text.setEditable(false);
            text.setOpaque(false);
            text.setBorder(null);
            text.setFont(regionList.getFont().deriveFont(style));
            return text;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends FileRegion> list, FileRegion region, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setOpaque(true);
            Color foreground = isSelected ? list.getSelectionForeground() : list.getForeground();
            name.setForeground(foreground);
            Color muted = UIManager.getColor("Label.disabledForeground");
            details.setForeground(isSelected || muted == null ? foreground : muted);
            FileVisualization vis = model.getVisualization();
            swatch.setIcon(new SwatchIcon(vis == null ? Color.gray : vis.getColorScheme().colorOf(region)));
            name.setText(region.getName());
            details.setText(describe(region));
            setToolTipText(region.getPath());
            // the text areas compute their height for this width
            int textWidth = Math.max(60, cellWidth(list) - getInsets().left - getInsets().right
                    - swatch.getPreferredSize().width - GAP);
            name.setSize(textWidth, Short.MAX_VALUE);
            details.setSize(textWidth, Short.MAX_VALUE);
            return this;
        }

        @Override
        public void paint(Graphics g) {
            // the list only sets the bounds, validate() would skip the layout of a renderer without peer
            layoutTree(this);
            super.paint(g);
        }

        private void layoutTree(Container container) {
            container.doLayout();
            for (Component child : container.getComponents()) {
                if (child instanceof Container) {
                    layoutTree((Container) child);
                }
            }
        }

        @Override
        public Dimension getPreferredSize() {
            // never wider than the list, so that the list never gets wider than its scroll pane
            Insets insets = getInsets();
            int textHeight = name.getPreferredSize().height + details.getPreferredSize().height;
            int height = Math.max(swatch.getPreferredSize().height, textHeight) + insets.top + insets.bottom;
            return new Dimension(cellWidth(regionList), height);
        }

        private int cellWidth(JList<?> list) {
            return list.getWidth() > 0 ? list.getWidth() : 280;
        }
    }

    private static String describe(FileRegion region) {
        StringBuilder details = new StringBuilder(StructureMap.hexRange(region.getStart(), region.getEnd()));
        if (region.isDeclaredOnly()) {
            details.append(", declared only");
        }
        if (region.isClipped()) {
            details.append(", clipped to the file");
        }
        for (Map.Entry<String, String> attribute : region.getAttributes().entrySet()) {
            details.append(NL).append(attribute.getKey()).append(": ").append(attribute.getValue());
        }
        return details.toString();
    }

    private final class RegionMouseHandler extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            FileRegion region = regionAt(e);
            if (region != null && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && zoomAction != null) {
                zoomAction.accept(region);
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            showPopup(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            showPopup(e);
        }

        private void showPopup(MouseEvent e) {
            FileRegion region = regionAt(e);
            if (!e.isPopupTrigger() || region == null) {
                return;
            }
            JPopupMenu menu = new JPopupMenu();
            if (zoomAction != null) {
                JMenuItem zoom = new JMenuItem("Zoom to " + region.getName());
                zoom.addActionListener(a -> zoomAction.accept(region));
                menu.add(zoom);
            }
            if (navigator != null) {
                JMenuItem tree = new JMenuItem("Show in tree");
                tree.addActionListener(a -> navigator.showInTree(region, region.getStart()));
                menu.add(tree);
            }
            if (menu.getComponentCount() > 0) {
                menu.show(regionList, e.getX(), e.getY());
            }
        }

        private FileRegion regionAt(MouseEvent e) {
            int index = regionList.locationToIndex(e.getPoint());
            if (index < 0 || !regionList.getCellBounds(index, index).contains(e.getPoint())) {
                return null;
            }
            regionList.setSelectedIndex(index);
            return regionListModel.get(index);
        }
    }

    /**
     * A small colored square.
     */
    static final class SwatchIcon implements Icon {
        private final Color color;
        private final boolean outlined;

        SwatchIcon(Color color) {
            this(color, false);
        }

        SwatchIcon(Color color, boolean outlined) {
            this.color = color;
            this.outlined = outlined;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            if (outlined) {
                g.drawRect(x + 1, y + 1, getIconWidth() - 3, getIconHeight() - 3);
            } else {
                g.fillRect(x, y, getIconWidth(), getIconHeight());
                g.setColor(Color.darkGray);
                g.drawRect(x, y, getIconWidth() - 1, getIconHeight() - 1);
            }
        }

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }
    }
}
