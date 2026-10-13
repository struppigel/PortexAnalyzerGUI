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
    private static final String NL = "\n";

    private final VisualizationModel model;
    private final LongSupplier bytesPerCell;
    private final JTextArea header = new JTextArea();
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
        header.setEditable(false);
        header.setRows(10); // fixed height, so that the list of structures does not jump
        header.setLineWrap(true);
        header.setWrapStyleWord(true);
        header.setFont(HexFont.font(Font.PLAIN, 13));
        header.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        regionList.setCellRenderer(new RegionRenderer());
        regionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        regionList.addMouseListener(new RegionMouseHandler());
        JLabel title = new JLabel("Structures, innermost first");
        title.setBorder(BorderFactory.createEmptyBorder(6, 4, 2, 4));
        JPanel regionPanel = new JPanel(new BorderLayout());
        regionPanel.add(title, BorderLayout.NORTH);
        regionPanel.add(new JScrollPane(regionList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        // the cells wrap at the width of the list, they must be measured again when it changes
        regionList.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                regionList.setFixedCellHeight(10);
                regionList.setFixedCellHeight(-1);
            }
        });
        add(header, BorderLayout.NORTH);
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
        StringBuilder text = new StringBuilder();
        if (vis == null) {
            text.append("No file loaded");
        } else if (offset < 0 || offset >= vis.getFileLength()) {
            text.append("Point at the visualization to see").append(NL)
                    .append("the structures and bytes there.").append(NL)
                    .append("Ctrl + wheel zooms, Ctrl + G goes to an offset.");
        } else {
            describeOffset(text, vis, offset);
        }
        if (vis != null && model.hasRangeSelection()) {
            describeRange(text, vis.getStructureMap().getFileLength(), vis.getSource());
        }
        header.setText(text.toString());
        updateRegions(vis, offset);
    }

    private void describeOffset(StringBuilder text, FileVisualization vis, long offset) {
        ByteSource source = vis.getSource();
        long cellSize = Math.max(1, bytesPerCell.getAsLong());
        long cellStart = offset - offset % cellSize;
        long cellEnd = Math.min(vis.getFileLength(), cellStart + cellSize);
        String kind = model.getHoverOffset() >= 0 ? "" : "  (selected)";
        text.append("Offset   ").append(format(offset)).append(kind).append(NL);
        try {
            int value = source.byteAt(offset);
            if (value >= 0) {
                text.append("Byte     ").append(String.format("%02X  %3d  '%s'", value, value, printable(value)))
                        .append(NL);
            }
            if (cellSize > 1) {
                text.append("Cell     ").append(format(cellStart)).append(" - ").append(format(cellEnd - 1))
                        .append(" (").append(cellEnd - cellStart).append(" bytes)").append(NL);
                byte[] bytes = new byte[(int) Math.min(SHOWN_CELL_BYTES, cellEnd - cellStart)];
                int read = source.read(cellStart, bytes, 0, bytes.length);
                text.append("         ").append(hexBytes(bytes, read)).append(NL);
            }
            describeStatistics(text, vis, cellStart, cellEnd);
        } catch (ByteSourceClosedException e) {
            // another file is being loaded
        } catch (RuntimeException e) {
            LOGGER.warn("Could not describe offset " + offset + ": " + e.getMessage());
        }
    }

    private void describeStatistics(StringBuilder text, FileVisualization vis, long cellStart, long cellEnd) {
        ByteStatistics statistics = vis.getStatistics();
        long cellSize = cellEnd - cellStart;
        double mean;
        double max;
        double[] fractions = new double[ByteClass.values().length];
        if (cellSize >= statistics.getBlockSize()) {
            if (cellEnd > statistics.getComputedLength()) {
                text.append("Entropy  computing ...").append(NL);
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
            text.append("Entropy  ").append(String.format("%.2f mean, %.2f max (bits per byte)", mean * 8, max * 8))
                    .append(NL);
        }
        text.append("Bytes    ").append(String.format("zero %.0f%%, 0xFF %.0f%%, text %.0f%%, control %.0f%%, high %.0f%%",
                100 * fractions[ByteClass.ZERO.ordinal()], 100 * fractions[ByteClass.MAX.ordinal()],
                100 * fractions[ByteClass.PRINTABLE.ordinal()], 100 * fractions[ByteClass.CONTROL.ordinal()],
                100 * fractions[ByteClass.HIGH.ordinal()])).append(NL);
    }

    private void describeRange(StringBuilder text, long fileLength, ByteSource source) {
        long start = model.getRangeStart();
        long end = Math.min(fileLength, model.getRangeEnd());
        text.append(NL).append("Range    ").append(format(start)).append(" - ").append(format(end - 1))
                .append(" (").append(end - start).append(" bytes)").append(NL);
        if (start == rangeEntropyStart && end == rangeEntropyEnd && rangeEntropy != null) {
            text.append("Entropy  ").append(String.format("%.2f bits per byte", rangeEntropy * 8)).append(NL);
        } else {
            text.append("Entropy  computing ...").append(NL);
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
        StringBuilder text = new StringBuilder(header.getText());
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

    private static String hexBytes(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
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
            setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
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
            // Nimbus paints the background of text areas even if they are not opaque, unless it is transparent
            text.setBackground(new Color(0, 0, 0, 0));
            text.setBorder(null);
            text.setFont(regionList.getFont().deriveFont(style));
            return text;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends FileRegion> list, FileRegion region, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            // copies, because Nimbus replaces colors of the look and feel (UIResource) with those of the panel
            Color background = new Color((isSelected ? list.getSelectionBackground() : list.getBackground()).getRGB());
            Color foreground = new Color((isSelected ? list.getSelectionForeground() : list.getForeground()).getRGB());
            setBackground(background);
            setOpaque(true);
            name.setForeground(foreground);
            details.setForeground(foreground);
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
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }
}
