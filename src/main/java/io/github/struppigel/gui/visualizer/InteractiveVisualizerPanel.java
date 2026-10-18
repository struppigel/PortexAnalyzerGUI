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

import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.gui.visualizer.ZoomLadder.ZoomLevel;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.engine.ViewMapping;
import io.github.struppigel.tools.visualizer.render.RenderStyle;
import io.github.struppigel.tools.visualizer.render.Track;
import io.github.struppigel.tools.visualizer.render.VisualizationRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * The interactive visualization of the file: overview, zoomable detail view and the description of the offset under
 * the mouse. Below the description, tabs show the structures at the offset or all structures by color.
 */
public class InteractiveVisualizerPanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEFAULT_SAVE_FILENAME = "portex_visualization.png";
    private static final int OVERVIEW_TRACK_WIDTH = 24;

    private final VisualizationModel model;
    private final OverviewStrip overview;
    private final DetailCanvas canvas;
    private final InfoPane infoPane;
    private final LegendPanel legendPanel;
    private JComboBox<String> hexColorChooser;
    private JComboBox<String> hexWidthChooser;
    private JLabel status;
    private IntConsumer hexWidthListener;
    private boolean settingHexWidth;

    public InteractiveVisualizerPanel(VisualizationModel model) {
        this(model, RenderSupport.RENDER_POOL);
    }

    InteractiveVisualizerPanel(VisualizationModel model, Executor executor) {
        super(new BorderLayout());
        this.model = model;
        this.overview = new OverviewStrip(model, OVERVIEW_TRACK_WIDTH, true, executor);
        this.canvas = new DetailCanvas(model, executor);
        this.infoPane = new InfoPane(model, canvas::getBytesPerCell);
        this.legendPanel = new LegendPanel(model);
        infoPane.setZoomAction(canvas::zoomToRegion);
        legendPanel.setZoomAction(canvas::zoomToRegion);
        infoPane.addTab("Navigate",
                "All structures by color: a click marks one, a double click unfolds it and zooms to it", legendPanel);
        initOverviewNavigation();

        JPanel visuals = new JPanel(new BorderLayout(Spacing.GAP, 0));
        visuals.setBorder(Spacing.padding(Spacing.GAP, Spacing.GAP, Spacing.GAP, 0));
        visuals.add(overview, BorderLayout.WEST);
        visuals.add(canvas, BorderLayout.CENTER);
        visuals.add(canvas.getScrollBar(), BorderLayout.EAST);

        JPanel main = new JPanel(new BorderLayout());
        main.add(visuals, BorderLayout.CENTER);
        main.add(createStatusBar(), BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, main, infoPane);
        split.setResizeWeight(1.0);
        split.setContinuousLayout(true);
        split.setBorder(null);

        add(createToolBar(), BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        initKeyBindings();
    }

    private JComponent createStatusBar() {
        JLabel status = new JLabel(" ");
        status.setBorder(Spacing.padding(Spacing.GAP / 2, Spacing.PAD, Spacing.GAP / 2, Spacing.PAD));
        status.setForeground(UIManager.getColor("Label.disabledForeground"));
        PortexSwingUtils.disableHtml(status);
        JPanel bar = new JPanel(new BorderLayout());
        bar.add(new JSeparator(), BorderLayout.NORTH);
        bar.add(status, BorderLayout.CENTER);
        VisualizationModel.Listener refresh = new VisualizationModel.Listener() {
            @Override
            public void visibleRangeChanged() {
                status.setText(statusText());
            }

            @Override
            public void dataChanged() {
                status.setText(statusText());
            }

            @Override
            public void styleChanged() {
                status.setText(statusText());
            }

            @Override
            public void selectionChanged() {
                status.setText(statusText()); // the marked bytes, also while dragging
            }
        };
        model.addListener(refresh);
        status.setText(statusText());
        status.setToolTipText("Drag to mark bytes, Shift + click extends the marking");
        this.status = status;
        return bar;
    }

    /**
     * @return the zoom level, the part of the file that is visible and the marked bytes
     */
    String statusText() {
        FileVisualization vis = model.getVisualization();
        if (vis == null || canvas.getLadder().isEmpty()) {
            return " ";
        }
        long bytesPerCell = canvas.getBytesPerCell();
        long end = Math.min(model.getVisibleEnd(), vis.getFileLength());
        String cells = canvas.level().hex ? "hex, " + canvas.level().columns + " bytes per line"
                : bytesPerCell + (bytesPerCell == 1 ? " byte" : " bytes") + " per cell";
        String text = String.format("Zoom %d of %d, %s, showing 0x%X - 0x%X of 0x%X",
                canvas.getLevelIndex() + 1, canvas.getLadder().size(), cells, model.getVisibleStart(),
                Math.max(0, end - 1), Math.max(0, vis.getFileLength() - 1));
        if (model.hasRangeSelection()) {
            long markedEnd = Math.min(model.getRangeEnd(), vis.getFileLength());
            text += String.format(", marked 0x%X - 0x%X (%s)", model.getRangeStart(), Math.max(0, markedEnd - 1),
                    ByteConversions.size(markedEnd - model.getRangeStart()));
        }
        return text;
    }

    /**
     * @return the status bar below the visualization, for tests
     */
    JLabel getStatus() {
        return status;
    }

    private void initOverviewNavigation() {
        MouseAdapter navigation = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                navigate(e);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    Track track = overview.trackAt(e.getPoint());
                    if (track != null) {
                        model.setHexBackground(track);
                    }
                    zoomToOffset(overview.offsetAt(e.getPoint()));
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                navigate(e);
            }

            private void navigate(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    long offset = overview.rowOffsetAt(e.getY());
                    if (offset >= 0) {
                        model.requestView(offset);
                    }
                }
            }
        };
        overview.addMouseListener(navigation);
        overview.addMouseMotionListener(navigation);
    }

    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);
        toolBar.setBorder(Spacing.padding(Spacing.GAP / 2, Spacing.GAP, Spacing.GAP / 2, Spacing.GAP));
        toolBar.add(button(ToolIcons.zoomIn(), "Zoom in (Ctrl + mouse wheel, +)", () -> canvas.zoom(1, null)));
        toolBar.add(button(ToolIcons.zoomOut(), "Zoom out (Ctrl + mouse wheel, -)", () -> canvas.zoom(-1, null)));
        toolBar.add(button(ToolIcons.wholeFile(), "Show the whole file", canvas::zoomToFit));
        toolBar.add(button(ToolIcons.goTo(), "Go to an offset (Ctrl + G)", canvas::goToOffset));
        toolBar.addSeparator();
        toolBar.add(new JLabel("Hex colors "));
        toolBar.add(hexColorChooser());
        toolBar.add(new JLabel("  Hex width "));
        toolBar.add(hexWidthChooser());
        toolBar.addSeparator();
        JButton exportButton = new JButton("Save image ...");
        exportButton.setToolTipText("Save the visualization as PNG");
        JPopupMenu exportMenu = new JPopupMenu();
        JMenuItem viewItem = new JMenuItem("Current view");
        viewItem.addActionListener(e -> saveImage(currentViewRenderer()));
        JMenuItem classicItem = new JMenuItem("Whole file (classic)");
        classicItem.addActionListener(e -> saveImage(classicRenderer()));
        exportMenu.add(viewItem);
        exportMenu.add(classicItem);
        exportButton.addActionListener(e -> exportMenu.show(exportButton, 0, exportButton.getHeight()));
        toolBar.add(exportButton);
        return toolBar;
    }

    private static final Track[] HEX_BACKGROUNDS = {Track.STRUCTURE, Track.BYTEPLOT, Track.ENTROPY};
    private static final String[] HEX_BACKGROUND_NAMES = {"Structure", "Byte plot", "Entropy"};

    /**
     * Shows and changes whose colors the hex cells have. Zooming in with the mouse over a track picks that track.
     */
    private JComboBox<String> hexColorChooser() {
        JComboBox<String> chooser = new JComboBox<>(HEX_BACKGROUND_NAMES);
        chooser.setToolTipText("Colors of the hex view, zooming in with the mouse over a track picks its colors");
        chooser.setFocusable(false);
        chooser.setMaximumSize(chooser.getPreferredSize());
        chooser.setSelectedIndex(java.util.Arrays.asList(HEX_BACKGROUNDS).indexOf(model.getHexBackground()));
        chooser.addActionListener(e -> model.setHexBackground(HEX_BACKGROUNDS[chooser.getSelectedIndex()]));
        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void styleChanged() {
                int index = java.util.Arrays.asList(HEX_BACKGROUNDS).indexOf(model.getHexBackground());
                if (chooser.getSelectedIndex() != index) {
                    chooser.setSelectedIndex(index);
                }
            }
        });
        this.hexColorChooser = chooser;
        return chooser;
    }

    JComboBox<String> getHexColorChooser() {
        return hexColorChooser;
    }

    /** bytes per line of the hex view, 0 fits the width of the view */
    private static final int[] HEX_WIDTHS = {0, 8, 16, 24, 32, 48, 64};

    private JComboBox<String> hexWidthChooser() {
        String[] names = new String[HEX_WIDTHS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = HEX_WIDTHS[i] == 0 ? "Auto" : HEX_WIDTHS[i] + " bytes";
        }
        JComboBox<String> chooser = new JComboBox<>(names);
        chooser.setToolTipText("Bytes per line of the hex view; Auto fits the width of the view, "
                + "larger values are limited to what fits");
        chooser.setFocusable(false);
        chooser.setMaximumSize(chooser.getPreferredSize());
        chooser.addActionListener(e -> {
            if (settingHexWidth) {
                return;
            }
            int bytes = HEX_WIDTHS[chooser.getSelectedIndex()];
            canvas.setHexBytesPerLine(bytes);
            if (hexWidthListener != null) {
                hexWidthListener.accept(bytes);
            }
        });
        this.hexWidthChooser = chooser;
        return chooser;
    }

    JComboBox<String> getHexWidthChooser() {
        return hexWidthChooser;
    }

    /**
     * @param bytesPerLine the bytes per line of the hex view, 0 or a value that is not offered fits the width of
     *                     the view
     */
    public void setHexBytesPerLine(int bytesPerLine) {
        int index = Math.max(0, java.util.Arrays.binarySearch(HEX_WIDTHS, bytesPerLine));
        settingHexWidth = true;
        try {
            hexWidthChooser.setSelectedIndex(index);
        } finally {
            settingHexWidth = false;
        }
        canvas.setHexBytesPerLine(HEX_WIDTHS[index]);
    }

    /**
     * @param listener receives the bytes per line when the user picks another width, 0 for auto
     */
    public void setHexBytesPerLineListener(IntConsumer listener) {
        this.hexWidthListener = listener;
    }

    private static JButton button(Icon icon, String tooltip, Runnable action) {
        JButton button = new JButton(icon);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private void initKeyBindings() {
        InputMap inputs = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        ActionMap actions = getActionMap();
        bind(inputs, actions, "zoomIn", () -> canvas.zoom(1, null),
                KeyStroke.getKeyStroke(KeyEvent.VK_PLUS, 0), KeyStroke.getKeyStroke(KeyEvent.VK_ADD, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), KeyStroke.getKeyStroke('+'));
        bind(inputs, actions, "zoomOut", () -> canvas.zoom(-1, null),
                KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, 0), KeyStroke.getKeyStroke(KeyEvent.VK_SUBTRACT, 0));
        bind(inputs, actions, "pageUp", () -> canvas.setFirstRow(canvas.getFirstRow() - pageRows()),
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0));
        bind(inputs, actions, "pageDown", () -> canvas.setFirstRow(canvas.getFirstRow() + pageRows()),
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0));
        bind(inputs, actions, "up", () -> canvas.setFirstRow(canvas.getFirstRow() - 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
        bind(inputs, actions, "down", () -> canvas.setFirstRow(canvas.getFirstRow() + 1),
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        bind(inputs, actions, "home", () -> canvas.setFirstRow(0), KeyStroke.getKeyStroke(KeyEvent.VK_HOME, 0));
        bind(inputs, actions, "end", () -> canvas.setFirstRow(Long.MAX_VALUE),
                KeyStroke.getKeyStroke(KeyEvent.VK_END, 0));
        bind(inputs, actions, "clearSelection", model::clearSelection,
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
        bind(inputs, actions, "goTo", canvas::goToOffset, KeyStroke.getKeyStroke(KeyEvent.VK_G, shortcutMask()));
    }

    /**
     * @return Ctrl, or the command key on macOS
     */
    private static int shortcutMask() {
        // the toolkit throws in headless mode, e.g., in tests
        return GraphicsEnvironment.isHeadless() ? InputEvent.CTRL_DOWN_MASK
                : Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    }

    private long pageRows() {
        return Math.max(1, canvas.getHeight() / Math.max(1, canvas.level().cellHeight) - 1);
    }

    private static void bind(InputMap inputs, ActionMap actions, String name, Runnable action, KeyStroke... keys) {
        for (KeyStroke key : keys) {
            if (key != null) {
                inputs.put(key, name);
            }
        }
        actions.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // --- export ---

    /**
     * @return renders the rows that are visible now, captures the view in the event dispatch thread
     */
    private Callable<BufferedImage> currentViewRenderer() {
        FileVisualization vis = model.getVisualization();
        if (vis == null || !canvas.relayout()) {
            return null;
        }
        ZoomLevel level = canvas.level();
        ViewMapping mapping = canvas.mapping();
        long firstRow = canvas.getFirstRow();
        int rows = (int) Math.max(1, Math.min(mapping.getRowCount() - firstRow,
                canvas.getHeight() / level.cellHeight));
        List<Track> tracks = new ArrayList<>();
        for (DetailCanvas.TrackLayout layout : canvas.trackLayouts()) {
            tracks.add(layout.track);
        }
        RenderStyle style = model.getHexStyle(); // the hex background only changes the hex and ASCII tracks
        return () -> VisualizationRenderer.renderComposite(vis, mapping, firstRow, rows, tracks, level.cellWidth,
                level.cellHeight, style, true);
    }

    private Callable<BufferedImage> classicRenderer() {
        if (model.getFileData() == null) {
            return null;
        }
        File file = model.getFileData().getFile();
        return () -> ClassicImageExporter.render(file, ClassicImageExporter.DEFAULT_HEIGHT);
    }

    private void saveImage(Callable<BufferedImage> renderer) {
        if (renderer == null) {
            return;
        }
        String path = PortexSwingUtils.getSaveFileNameFromUser(this, DEFAULT_SAVE_FILENAME);
        if (path == null) {
            return;
        }
        if (!path.toLowerCase().endsWith(".png")) {
            path += ".png";
        }
        File target = new File(path);
        if (PortexSwingUtils.checkIfFileExistsAndAskIfOverwrite(this, target)) {
            new SaveImageWorker(target, renderer).execute();
        }
    }

    private class SaveImageWorker extends SwingWorker<Boolean, Void> {
        private final File target;
        private final Callable<BufferedImage> renderer;

        SaveImageWorker(File target, Callable<BufferedImage> renderer) {
            this.target = target;
            this.renderer = renderer;
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            return ImageIO.write(renderer.call(), "png", target);
        }

        @Override
        protected void done() {
            boolean success = false;
            try {
                success = get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Could not save the image", e);
            }
            if (success) {
                JOptionPane.showMessageDialog(InteractiveVisualizerPanel.this,
                        "File successfully saved under " + target, "Success", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(InteractiveVisualizerPanel.this,
                        "Unable to save file " + target, "Something went wrong :(", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- configuration ---

    public void setHexEnabled(boolean hexEnabled) {
        canvas.setHexEnabled(hexEnabled);
        infoPane.setHexEnabled(hexEnabled);
    }

    /**
     * @param navigator shows structures in the tree, used by the context menus
     */
    public void setRegionNavigator(RegionNavigator navigator) {
        canvas.setRegionNavigator(navigator);
        infoPane.setRegionNavigator(navigator);
        legendPanel.setRegionNavigator(navigator);
    }

    /**
     * Zooms in to the offset, see {@link DetailCanvas#zoomToOffset(long)}.
     */
    public void zoomToOffset(long offset) {
        canvas.zoomToOffset(offset);
    }

    /**
     * @param listener receives the offset when the user clicks into the detail view
     */
    public void setOffsetClickListener(LongConsumer listener) {
        canvas.setClickListener(listener);
    }

    DetailCanvas getCanvas() {
        return canvas;
    }

    OverviewStrip getOverview() {
        return overview;
    }

    InfoPane getInfoPane() {
        return infoPane;
    }

    LegendPanel getLegendPanel() {
        return legendPanel;
    }
}
