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
import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.tools.ShannonEntropy;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.visualizer.engine.ByteSource;
import io.github.struppigel.tools.visualizer.engine.ByteSourceClosedException;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.ToLongFunction;

/**
 * Shows the offset under the mouse, or the selected one, and the range of the selected bytes. Below, tabs list the
 * structures at the offset and the structures in the selection, and describe the bytes: the values of the selected
 * bytes and the bytes at the offset. Other views can share the room, see {@link #addTab(String, String, Component)}.
 */
public class InfoPane extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int UPDATE_DELAY_MS = 33;
    private static final int SHOWN_CELL_BYTES = 16;
    private static final int SHOWN_BYTES_PER_ROW = 8;
    private static final String NL = "\n";
    /** more structures of a selection are not listed, measuring the wrapping cells takes too long */
    static final int MAX_SELECTION_REGIONS = 500;
    static final String SELECTION_HINT = "Mark bytes or select a structure to see the structures in it.";

    private final VisualizationModel model;
    private final LongSupplier bytesPerCell;
    // the offset and the selected range, always visible
    private final InfoGrid grid = new InfoGrid();
    // the values of the selected bytes and the bytes at the offset, in a tab
    private final InfoGrid bytesGrid = new InfoGrid();
    private final RegionListView pointerView;
    private final RegionListView selectionView;
    private final JLabel selectionCaption = new JLabel(SELECTION_HINT);
    private final JPanel selectionPanel = new JPanel(new BorderLayout());
    private final JTabbedPane tabs = new JTabbedPane();
    private final Timer updateTimer;
    private boolean hexEnabled = true;
    private Consumer<FileRegion> zoomAction;
    private RegionNavigator navigator;

    private long shownOffset = Long.MIN_VALUE;
    // true while the selection tab marks a structure, so that the tab keeps the structures it lists
    private boolean selectingFromSelectionList;
    private boolean selectionListStale = true;
    // the selected bytes whose values are shown, to let the grid shrink when they change
    private long[] describedSelection;
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
        // marks the structure in the views; the offset stays, so that this list keeps its content
        pointerView = new RegionListView(region -> model.showRegion(region,
                shownOffset >= 0 ? shownOffset : region.getStart()),
                // the listed structures are at the offset
                region -> shownOffset);
        // marks the structure, the list keeps the structures of the selection it was made for
        selectionView = new RegionListView(region -> {
            selectingFromSelectionList = true;
            try {
                model.showRegion(region, region.getStart());
            } finally {
                selectingFromSelectionList = false;
            }
        }, region -> RegionMenu.selectedOffsetIn(model, region));

        selectionCaption.setForeground(UIManager.getColor("Label.disabledForeground"));
        selectionCaption.setBorder(Spacing.padding(Spacing.GAP, Spacing.PAD, Spacing.GAP / 2, Spacing.PAD));
        PortexSwingUtils.disableHtml(selectionCaption);
        selectionPanel.add(selectionCaption, BorderLayout.NORTH);
        selectionPanel.add(selectionView.getScrollPane(), BorderLayout.CENTER);

        // the rows show the first selected bytes, copying all copies all of them in the format of the row
        bytesGrid.setCopyAllItem(this::copyAllItem);
        WidthTrackingPanel bytesView = new WidthTrackingPanel(new BorderLayout(), 16);
        bytesView.add(bytesGrid, BorderLayout.NORTH);
        JScrollPane bytesScroll = new JScrollPane(bytesView, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        bytesScroll.setBorder(null);

        // otherwise a click on a tab gives it the focus, then the arrow keys switch tabs instead of scrolling the view
        tabs.setFocusable(false);
        // the tabs stay in one row in a narrow side panel
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.setBorder(Spacing.padding(Spacing.GAP, 0, 0, 0));
        addTab("At pointer", "The structures at the offset under the mouse or the selected one, innermost first",
                pointerView.getScrollPane());
        addTab("In selection", "The structures in the marked bytes or the selected structure, innermost first",
                selectionPanel);
        addTab("Bytes", "The values of the marked bytes or the selected structure, and the bytes at the pointer",
                bytesScroll);
        // the structures of the selection are only listed while they are shown
        tabs.addChangeListener(e -> updateSelectionList(model.getVisualization()));
        add(grid, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 400));

        updateTimer = new Timer(UPDATE_DELAY_MS, e -> updateNow());
        updateTimer.setRepeats(false);
        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void dataChanged() {
                shownOffset = Long.MIN_VALUE;
                selectionListStale = true;
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
                if (!selectingFromSelectionList) {
                    selectionListStale = true;
                }
                scheduleUpdate();
            }
        });
        updateNow();
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        shownOffset = Long.MIN_VALUE;
        selectionListStale = true; // the caption shows offsets
        updateNow();
    }

    void setZoomAction(Consumer<FileRegion> zoomAction) {
        this.zoomAction = zoomAction;
    }

    void setRegionNavigator(RegionNavigator navigator) {
        this.navigator = navigator;
    }

    /**
     * Adds a tab below the description of the offset, after the structures at the offset and in the selection.
     */
    void addTab(String title, String tooltip, Component content) {
        tabs.addTab(title, null, content, tooltip);
    }

    /**
     * @return the tabs below the description, for tests
     */
    JTabbedPane getTabs() {
        return tabs;
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
        boolean atOffset = vis != null && offset >= 0 && offset < vis.getFileLength();
        long[] selected = vis == null ? null : model.getSelectedBytes();

        // always visible: the offset and the selected range
        List<String[]> rows = new ArrayList<>();
        if (vis == null) {
            rows.add(new String[]{null, "No file loaded"});
        } else if (!atOffset) {
            rows.add(new String[]{null, "Point at the visualization to see the bytes there."});
        } else {
            // copying copies only the offset
            rows.add(new String[]{"Offset", format(offset) + (model.getHoverOffset() >= 0 ? "" : "  (selected)"),
                    InfoGrid.MONO, format(offset)});
        }
        if (selected != null) {
            rows.add(mono("Range", rangeText(selected)));
        }

        // the selected bytes first, so that their rows do not move when the pointer moves
        List<String[]> bytesRows = new ArrayList<>();
        if (selected != null) {
            bytesRows.add(title("Selected bytes"));
            if (!model.hasRangeSelection()) {
                describeRegion(bytesRows, model.getSelectedRegion(), vis.getStructureMap().getFileLength());
            } else {
                addExactEntropy(bytesRows, "Entropy", selected[0], selected[1]);
            }
            describeSelectedBytes(bytesRows, vis, selected);
        }
        if (atOffset) {
            bytesRows.add(title("At the pointer"));
            describeBytesAt(bytesRows, vis, offset);
        } else if (vis != null) {
            bytesRows.add(new String[]{null, "Ctrl + wheel zooms, Ctrl + G goes to an offset."});
            bytesRows.add(new String[]{null, "Drag to mark bytes and see their values here, "
                    + "Shift + click extends the marking."});
        }

        if (!Arrays.equals(selected, describedSelection)) {
            describedSelection = selected;
            grid.resetHeight();
            bytesGrid.resetHeight();
        }
        grid.setRows(rows);
        bytesGrid.setRows(bytesRows);
        updateRegions(vis, offset);
        updateSelectionList(vis);
    }

    private static String[] mono(String label, String value) {
        return new String[]{label, value, InfoGrid.MONO};
    }

    private static String[] title(String text) {
        return new String[]{null, text, InfoGrid.TITLE};
    }

    /**
     * Adds the byte at the offset and, if a cell has several bytes, the bytes of the cell.
     */
    private void describeBytesAt(List<String[]> rows, FileVisualization vis, long offset) {
        ByteSource source = vis.getSource();
        long cellSize = Math.max(1, bytesPerCell.getAsLong());
        long cellStart = offset - offset % cellSize;
        long cellEnd = Math.min(vis.getFileLength(), cellStart + cellSize);
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
        } catch (ByteSourceClosedException e) {
            // another file is being loaded
        } catch (RuntimeException e) {
            LOGGER.warn("Could not describe offset " + offset + ": " + e.getMessage());
        }
    }

    /**
     * The selected structure and the entropy of the whole structure, computed like the one of the overlay in the
     * tree.
     */
    private void describeRegion(List<String[]> rows, FileRegion region, long fileLength) {
        rows.add(new String[]{"Structure", region.getName()});
        long start = region.getStart();
        long end = Math.min(fileLength, region.getEnd());
        if (end > start) {
            addExactEntropy(rows, "Region entropy", start, end);
        }
    }

    /**
     * Adds the values of the selected bytes: numbers, times, GUID, hex and text, as far as they make sense for the
     * size.
     */
    private void describeSelectedBytes(List<String[]> rows, FileVisualization vis, long[] selected) {
        long size = selected[1] - selected[0];
        try {
            byte[] bytes = new byte[(int) Math.min(ByteConversions.SHOWN_BYTES, size)];
            int read = vis.getSource().read(selected[0], bytes, 0, bytes.length);
            // the font of the values, see InfoGrid
            Font font = HexFont.font(Font.PLAIN, 13);
            rows.addAll(ByteConversions.rows(Arrays.copyOf(bytes, Math.max(0, read)), size, font::canDisplay));
        } catch (ByteSourceClosedException e) {
            // another file is being loaded
        } catch (RuntimeException e) {
            LOGGER.warn("Could not read the selected bytes at " + selected[0] + ": " + e.getMessage());
        }
    }

    /**
     * @param label the label of the value that the menu of the bytes tab is for, null if none
     * @return an item that copies all selected bytes in the format of the value, hex for values that are no text;
     *         disabled if they are too many; null if no bytes are selected
     */
    private JMenuItem copyAllItem(String label) {
        FileVisualization vis = model.getVisualization();
        long[] selected = vis == null ? null : model.getSelectedBytes();
        if (selected == null) {
            return null;
        }
        long size = selected[1] - selected[0];
        ByteCopy.Format format = ByteCopy.Format.ofLabel(label);
        JMenuItem item = new JMenuItem("Copy all as " + format.getTitle() + " (" + ByteConversions.size(size) + ")");
        String refusal = ByteCopy.refusal(size, format);
        if (refusal != null) {
            item.setEnabled(false);
            item.setToolTipText(refusal);
        } else {
            // the UTF-16 text has the same dots as the row, see describeSelectedBytes
            Font font = HexFont.font(Font.PLAIN, 13);
            item.addActionListener(a -> ByteCopy.copyInBackground(this, vis.getSource(), selected[0], selected[1],
                    format, font::canDisplay));
        }
        return item;
    }

    private String rangeText(long[] range) {
        long size = range[1] - range[0];
        return format(range[0]) + " - " + format(range[1] - 1) + " (" + size + (size == 1 ? " byte)" : " bytes)");
    }

    private void addExactEntropy(List<String[]> rows, String label, long start, long end) {
        if (start == rangeEntropyStart && end == rangeEntropyEnd && rangeEntropy != null) {
            rows.add(new String[]{label, String.format("%.2f bits per byte", rangeEntropy * 8)});
        } else {
            rows.add(new String[]{label, "computing ..."});
            computeRangeEntropy(model.getFileData().getFile(), start, end);
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
        if (vis == null || offset < 0) {
            pointerView.show(Collections.<FileRegion>emptyList());
            return;
        }
        pointerView.show(vis.getStructureMap().regionsAt(offset));
    }

    /**
     * Lists the structures of the selected bytes if the selection changed elsewhere and the tab is shown.
     */
    private void updateSelectionList(FileVisualization vis) {
        if (!selectionListStale || tabs.getSelectedComponent() != selectionPanel) {
            return;
        }
        selectionListStale = false;
        long[] selected = vis == null ? null : model.getSelectedBytes();
        if (selected == null) {
            selectionView.show(Collections.<FileRegion>emptyList());
            selectionCaption.setText(SELECTION_HINT);
            return;
        }
        List<FileRegion> regions = vis.getStructureMap().regionsOverlapping(selected[0], selected[1]);
        int shown = Math.min(regions.size(), MAX_SELECTION_REGIONS);
        selectionView.show(regions.subList(0, shown));
        String count = shown < regions.size() ? "first " + shown + " of " + regions.size() + " structures"
                : regions.size() + (regions.size() == 1 ? " structure" : " structures");
        selectionCaption.setText(rangeText(selected) + ", " + count);
    }

    /**
     * @return the shown text, the text of the bytes tab and the names of the structures at the offset, for tests
     */
    String getInfoText() {
        StringBuilder text = new StringBuilder(grid.getText());
        text.append(NL).append(bytesGrid.getText());
        ListModel<FileRegion> regions = pointerView.getList().getModel();
        for (int i = 0; i < regions.getSize(); i++) {
            text.append(NL).append(regions.getElementAt(i).getName());
        }
        return text.toString();
    }

    /**
     * @return the list of the structures at the described offset, for tests
     */
    JList<FileRegion> getRegionList() {
        return pointerView.getList();
    }

    /**
     * @return the list of the structures in the selection, for tests
     */
    JList<FileRegion> getSelectionList() {
        return selectionView.getList();
    }

    /**
     * @return what the list of the structures in the selection shows, for tests
     */
    String getSelectionCaption() {
        return selectionCaption.getText();
    }

    /**
     * @return the grid with the offset and the range, for tests
     */
    InfoGrid getGrid() {
        return grid;
    }

    /**
     * @return the grid in the bytes tab, for tests
     */
    InfoGrid getBytesGrid() {
        return bytesGrid;
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
     * A list of structures in a scroll pane. A click marks a structure, a double click zooms to it.
     */
    private final class RegionListView {
        private final DefaultListModel<FileRegion> listModel = new DefaultListModel<>();
        private final JList<FileRegion> list = new JList<FileRegion>(listModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return true; // as wide as the visible area, the cells wrap their text
            }

            @Override
            public JToolTip createToolTip() {
                // the paths contain names from the file, e.g., of sections
                JToolTip tip = super.createToolTip();
                PortexSwingUtils.disableHtml(tip);
                return tip;
            }
        };
        private final JScrollPane scroll;

        /**
         * @param clickAction   marks the clicked structure
         * @param currentOffset an offset in the location of a structure that is shown now, -1 if none is, for the
         *                      context menu
         */
        RegionListView(Consumer<FileRegion> clickAction, ToLongFunction<FileRegion> currentOffset) {
            list.setCellRenderer(new RegionRenderer(list));
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.addMouseListener(new RegionMouseHandler(list, clickAction, currentOffset));
            // flat on the color of the panel, like the grid above
            list.setBackground(InfoPane.this.getBackground());
            scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(InfoPane.this.getBackground());
            // the cells wrap at the width of the list, they must be measured again when it changes
            list.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    list.setFixedCellHeight(10);
                    list.setFixedCellHeight(-1);
                }
            });
        }

        void show(List<FileRegion> regions) {
            listModel.clear();
            listModel.addAll(regions);
        }

        JList<FileRegion> getList() {
            return list;
        }

        JScrollPane getScrollPane() {
            return scroll;
        }
    }

    /**
     * A structure as color swatch, name and details. The texts wrap at the width of the list, also within long
     * words, so that nothing is cut off.
     */
    private final class RegionRenderer extends JPanel implements ListCellRenderer<FileRegion> {
        private static final int GAP = 6;
        private final JList<FileRegion> owner;
        private final JLabel swatch = new JLabel();
        private final JTextArea name;
        private final JTextArea details;

        /**
         * @param owner the list that the renderer draws the cells of
         */
        RegionRenderer(JList<FileRegion> owner) {
            super(new BorderLayout(GAP, 0));
            this.owner = owner;
            name = wrappingText(Font.BOLD);
            details = wrappingText(Font.PLAIN);
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
            text.setFont(owner.getFont().deriveFont(style));
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
            int textWidth = Math.max(60, cellWidth() - getInsets().left - getInsets().right
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
            return new Dimension(cellWidth(), height);
        }

        private int cellWidth() {
            return owner.getWidth() > 0 ? owner.getWidth() : 280;
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
        private final JList<FileRegion> list;
        private final Consumer<FileRegion> clickAction;
        private final ToLongFunction<FileRegion> currentOffset;

        RegionMouseHandler(JList<FileRegion> list, Consumer<FileRegion> clickAction,
                           ToLongFunction<FileRegion> currentOffset) {
            this.list = list;
            this.clickAction = clickAction;
            this.currentOffset = currentOffset;
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            FileRegion region = regionAt(e);
            if (region == null || !SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            if (e.getClickCount() == 1) {
                clickAction.accept(region);
            } else if (e.getClickCount() == 2 && zoomAction != null) {
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
            RegionMenu.create(list, model, region, currentOffset.applyAsLong(region), zoomAction, navigator)
                    .show(list, e.getX(), e.getY());
        }

        private FileRegion regionAt(MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            if (index < 0 || !list.getCellBounds(index, index).contains(e.getPoint())) {
                return null;
            }
            list.setSelectedIndex(index);
            return list.getModel().getElementAt(index);
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
