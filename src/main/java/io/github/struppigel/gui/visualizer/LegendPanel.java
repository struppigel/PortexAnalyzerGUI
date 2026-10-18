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

import com.formdev.flatlaf.FlatClientProperties;
import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.PaintMode;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.render.LegendEntry;
import io.github.struppigel.tools.visualizer.render.LegendRenderer;
import io.github.struppigel.tools.visualizer.render.RegionColorScheme;
import io.github.struppigel.tools.visualizer.render.RenderStyle;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Switches the layers of the visualization on and off, shows their colors and lists the structures. Each layer has a
 * section with a check box, the sections stay when the layer is off. The structure layer lists all structures of
 * the file, nested like in the file, with their colors; the markers are listed by color. A click on a structure marks
 * it in the views, a double click unfolds it and zooms to it. A click on the arrow of a row unfolds it.
 */
public class LegendPanel extends JPanel {

    private static final int ENTRY_HEIGHT = 22;
    private static final int ALPHA_OFF = 90;
    private static final int DETAIL_GAP = 8;
    /** more structures of one color are not listed, the tree would get too long */
    static final int MAX_LISTED = 2000;
    private static final Set<RegionType> MARKER_TYPES = EnumSet.of(RegionType.ANOMALY, RegionType.SIGNATURE_MATCH);
    private static final Comparator<FileRegion> BY_OFFSET =
            Comparator.comparingLong(FileRegion::getStart).thenComparingInt(FileRegion::getDepth);

    private final VisualizationModel model;
    private final List<Section> sections = new ArrayList<>();
    private Consumer<FileRegion> zoomAction;
    private RegionNavigator navigator;

    public LegendPanel(VisualizationModel model) {
        super(new BorderLayout());
        this.model = model;
        // byte plot and entropy have colors, but no structures
        sections.add(new Section("Byte plot", model::isByteplotVisible, model::setByteplotVisible, true));
        sections.add(new Section("Entropy", model::isEntropyVisible, model::setEntropyVisible, true));
        sections.add(new Section("Structure", model::isStructureVisible, model::setStructureVisible, false));
        sections.add(new Section("Markers", model::isMarkersVisible, model::setMarkersVisible, false));

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                stack.add(new JSeparator());
            }
            stack.add(sections.get(i));
        }
        // the sections are as wide as the view, at the top
        WidthTrackingPanel view = new WidthTrackingPanel(new BorderLayout(), ENTRY_HEIGHT);
        view.add(stack, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void dataChanged() {
                rebuild();
            }

            @Override
            public void styleChanged() {
                // the colors stay the same, so the unfolded colors stay unfolded
                for (Section section : sections) {
                    section.applyState();
                }
            }

            @Override
            public void selectionChanged() {
                for (Section section : sections) {
                    section.syncSelection();
                }
            }
        });
        rebuild();
    }

    /**
     * @param zoomAction zooms to a structure, used by double clicks and the context menu
     */
    void setZoomAction(Consumer<FileRegion> zoomAction) {
        this.zoomAction = zoomAction;
    }

    /**
     * @param navigator shows structures in the tree, used by the context menu
     */
    void setRegionNavigator(RegionNavigator navigator) {
        this.navigator = navigator;
    }

    /**
     * Shows the colors and structures of the loaded file, all nodes folded.
     */
    private void rebuild() {
        FileVisualization vis = model.getVisualization();
        if (vis == null) {
            for (Section section : sections) {
                section.show(Collections.<LegendEntry>emptyList(), new DefaultMutableTreeNode());
            }
            return;
        }
        RegionColorScheme scheme = vis.getColorScheme();
        List<LegendEntry> bytePlot = withoutTitles(LegendRenderer.bytePlotEntries(scheme));
        List<LegendEntry> entropy = withoutTitles(LegendRenderer.entropyEntries(scheme));
        List<LegendEntry> markers = withoutTitles(LegendRenderer.entries(vis.getStructureMap(), scheme, false, false,
                MARKER_TYPES));
        sections.get(0).show(bytePlot, categoriesRoot(bytePlot, MAX_LISTED));
        sections.get(1).show(entropy, categoriesRoot(entropy, MAX_LISTED));
        sections.get(2).show(Collections.<LegendEntry>emptyList(),
                structureRoot(vis.getStructureMap(), scheme, MAX_LISTED));
        sections.get(3).show(markers, categoriesRoot(markers, MAX_LISTED));
    }

    private static List<LegendEntry> withoutTitles(List<LegendEntry> entries) {
        List<LegendEntry> result = new ArrayList<>();
        for (LegendEntry entry : entries) {
            if (!entry.isTitle()) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * @return the number of rows at the top of all sections
     */
    int entryCount() {
        int count = 0;
        for (Section section : sections) {
            count += ((DefaultMutableTreeNode) section.treeModel.getRoot()).getChildCount();
        }
        return count;
    }

    /**
     * @param entries   the colors of a layer
     * @param maxListed the number of structures that are listed at most below one color
     * @return the root of the colors, each with its structures
     */
    static DefaultMutableTreeNode categoriesRoot(List<LegendEntry> entries, int maxListed) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode();
        for (LegendEntry entry : entries) {
            root.add(categoryNode(entry, maxListed));
        }
        return root;
    }

    /**
     * @param map       the structures of the file
     * @param scheme    the colors of the structures
     * @param maxListed the number of structures that are listed at most below one node
     * @return the root of all structures of the map except the markers, nested like in the map; the headers are
     *         at the top instead of in a node of their own, and the sections are below the section table
     */
    static DefaultMutableTreeNode structureRoot(StructureMap map, RegionColorScheme scheme, int maxListed) {
        return new StructureTreeBuilder(map, scheme, maxListed).build();
    }

    /**
     * @param entry     a color of the legend
     * @param maxListed the number of structures that are listed at most
     * @return the node of the color, with the structures as children if there are several
     */
    static DefaultMutableTreeNode categoryNode(LegendEntry entry, int maxListed) {
        Category category = new Category(entry);
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(category);
        List<FileRegion> regions = category.getRegions();
        if (regions.size() > 1) {
            List<DefaultMutableTreeNode> children = new ArrayList<>();
            for (int i = 0; i < Math.min(regions.size(), maxListed); i++) {
                children.add(new DefaultMutableTreeNode(regions.get(i), false));
            }
            addListed(node, children, regions.size(), maxListed);
        }
        return node;
    }

    private static void addListed(DefaultMutableTreeNode parent, List<DefaultMutableTreeNode> children, int maxListed) {
        addListed(parent, children, children.size(), maxListed);
    }

    /**
     * Adds the first children, and the number of the others as last child if there are more than listed.
     *
     * @param total the number of children there are, at least the number of given children
     */
    private static void addListed(DefaultMutableTreeNode parent, List<DefaultMutableTreeNode> children, int total,
                                  int maxListed) {
        int listed = Math.min(children.size(), maxListed);
        for (int i = 0; i < listed; i++) {
            parent.add(children.get(i));
        }
        if (listed < total) {
            parent.add(new DefaultMutableTreeNode((total - listed) + " more are not listed", false));
        }
    }

    /**
     * @return the structure that the row stands for: a structure, a listed structure of a color or a color with
     *         exactly one structure, null for other rows
     */
    static FileRegion regionOf(TreePath path) {
        if (path == null) {
            return null;
        }
        Object item = ((DefaultMutableTreeNode) path.getLastPathComponent()).getUserObject();
        if (item instanceof Structure) {
            return ((Structure) item).getRegion();
        }
        if (item instanceof FileRegion) {
            return (FileRegion) item;
        }
        if (item instanceof Category && ((Category) item).getRegions().size() == 1) {
            return ((Category) item).getRegions().get(0);
        }
        return null;
    }

    private static Color dimmed(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), ALPHA_OFF);
    }

    private static Color mutedForeground() {
        Color muted = UIManager.getColor("Label.disabledForeground");
        return muted == null ? Color.gray : muted;
    }

    /**
     * A color of the legend and its structures by offset.
     */
    static final class Category {
        private final LegendEntry entry;
        private final List<FileRegion> regions;

        Category(LegendEntry entry) {
            this.entry = entry;
            List<FileRegion> sorted = new ArrayList<>(entry.getRegions());
            sorted.sort(BY_OFFSET);
            this.regions = Collections.unmodifiableList(sorted);
        }

        LegendEntry getEntry() {
            return entry;
        }

        List<FileRegion> getRegions() {
            return regions;
        }

        @Override
        public String toString() {
            return entry.getLabel();
        }
    }

    /**
     * A structure of the file with the label of its row and its color, null if the visualization does not paint it.
     * The structure is null for a section without bytes in the file.
     */
    static final class Structure {
        private final FileRegion region;
        private final String label;
        private final Color color;
        private final boolean outlined;

        Structure(FileRegion region, String label, Color color, boolean outlined) {
            this.region = region;
            this.label = label;
            this.color = color;
            this.outlined = outlined;
        }

        FileRegion getRegion() {
            return region;
        }

        String getLabel() {
            return label;
        }

        Color getColor() {
            return color;
        }

        boolean isOutlined() {
            return outlined;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Builds the tree of the structures of a structure map. Each section is listed once below the section table,
     * as its bytes, with its section header below it.
     */
    private static final class StructureTreeBuilder {
        private final StructureMap map;
        private final RegionColorScheme scheme;
        private final int maxListed;
        private final RenderStyle style = RenderStyle.defaults();
        // the sections by number, listed below their headers in the section table
        private final Map<String, FileRegion> sections = new LinkedHashMap<>();
        private final Set<FileRegion> placedSections = Collections.newSetFromMap(new IdentityHashMap<>());

        StructureTreeBuilder(StructureMap map, RegionColorScheme scheme, int maxListed) {
            this.map = map;
            this.scheme = scheme;
            this.maxListed = maxListed;
        }

        DefaultMutableTreeNode build() {
            if (!map.getRegions(RegionType.SECTION_TABLE).isEmpty()) {
                for (FileRegion section : map.getRegions(RegionType.SECTION)) {
                    String number = section.getAttributes().get(FileRegion.ATTR_SECTION_NUMBER);
                    if (number != null) {
                        sections.putIfAbsent(number, section);
                    }
                }
            }
            List<DefaultMutableTreeNode> top = new ArrayList<>();
            for (FileRegion region : map.getRoots()) {
                if (region.getType() == RegionType.HEADERS) {
                    addAll(top, region.getChildren());
                } else {
                    add(top, region);
                }
            }
            // sections whose header is not in the section table stay at the top
            for (FileRegion section : sections.values()) {
                if (!placedSections.contains(section)) {
                    top.add(node(section, sectionLabel(section)));
                }
            }
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();
            addListed(root, top, maxListed);
            return root;
        }

        private void addAll(List<DefaultMutableTreeNode> nodes, List<FileRegion> regions) {
            for (FileRegion region : regions) {
                add(nodes, region);
            }
        }

        private void add(List<DefaultMutableTreeNode> nodes, FileRegion region) {
            if (MARKER_TYPES.contains(region.getType())) {
                return; // in the markers section
            }
            if (region.getType() == RegionType.SECTION) {
                if (sections.containsValue(region)) {
                    return; // below its header
                }
                nodes.add(node(region, sectionLabel(region)));
                return;
            }
            nodes.add(node(region, region.getName()));
        }

        private DefaultMutableTreeNode node(FileRegion region, String label) {
            DefaultMutableTreeNode node = new DefaultMutableTreeNode(structure(region, label));
            List<DefaultMutableTreeNode> children = new ArrayList<>();
            for (FileRegion child : region.getChildren()) {
                if (child.getType() == RegionType.SECTION_HEADER && !sections.isEmpty()) {
                    children.add(sectionNode(child));
                } else {
                    add(children, child);
                }
            }
            addListed(node, children, maxListed);
            return node;
        }

        /**
         * @return the section of the header with the header below it; a section without bytes in the file, e.g.,
         *         uninitialized data, is a row that stands for no structure, so that every section has the same rows
         */
        private DefaultMutableTreeNode sectionNode(FileRegion header) {
            FileRegion section = sections.get(header.getAttributes().get(FileRegion.ATTR_SECTION_NUMBER));
            DefaultMutableTreeNode node;
            if (section == null) {
                node = new DefaultMutableTreeNode(new Structure(null, sectionLabel(header), null, false));
            } else {
                placedSections.add(section);
                node = new DefaultMutableTreeNode(structure(section, sectionLabel(section)));
            }
            List<DefaultMutableTreeNode> children = new ArrayList<>();
            children.add(node(header, "Section Header"));
            if (section != null) {
                for (FileRegion child : section.getChildren()) {
                    add(children, child);
                }
            }
            addListed(node, children, maxListed);
            return node;
        }

        private Structure structure(FileRegion region, String label) {
            if (!style.paints(region.getType())) {
                return new Structure(region, label, null, false);
            }
            return new Structure(region, label, scheme.colorOf(region),
                    region.getType().getPaintMode() != PaintMode.FILL);
        }

        /**
         * @return the number and name of a section or section header like in the other views, e.g. "1. .text"
         */
        private static String sectionLabel(FileRegion region) {
            String number = region.getAttributes().get(FileRegion.ATTR_SECTION_NUMBER);
            String name = region.getAttributes().get(FileRegion.ATTR_SECTION_NAME);
            if (number == null) {
                return region.getName();
            }
            return number + ". " + (name == null || name.isEmpty() ? "(no name)" : name);
        }
    }

    /**
     * A layer: a check box that switches it, an arrow that folds the tree away and the tree of its colors or
     * structures. Layers without structures show their colors next to the check box while they are folded.
     */
    private final class Section extends JPanel {

        private final DefaultTreeModel treeModel = new DefaultTreeModel(new DefaultMutableTreeNode());
        private final NavigateTree tree = new NavigateTree(treeModel);
        // the rows of the structures, to show the selection of the views
        private final Map<FileRegion, TreePath> paths = new IdentityHashMap<>();
        private final JCheckBox check;
        private final BooleanSupplier layerVisible;
        private final JButton arrow = new JButton();
        private final Box preview = Box.createHorizontalBox();
        private final boolean colorsOnly;
        private boolean folded;

        /**
         * @param colorsOnly true for a layer without structures, it starts folded and shows its colors in the header
         */
        Section(String title, BooleanSupplier layerVisible, Consumer<Boolean> setLayerVisible, boolean colorsOnly) {
            super(new BorderLayout());
            this.layerVisible = layerVisible;
            this.colorsOnly = colorsOnly;
            this.folded = colorsOnly;
            check = new JCheckBox(title, layerVisible.getAsBoolean());
            check.setFont(check.getFont().deriveFont(Font.BOLD));
            check.setFocusable(false);
            check.setToolTipText("Show or hide " + title.toLowerCase());
            check.addActionListener(e -> {
                setLayerVisible.accept(check.isSelected());
                applyState();
            });

            arrow.setIcon(new ArrowIcon(false));
            arrow.setBorderPainted(false);
            arrow.setContentAreaFilled(false);
            arrow.setFocusable(false);
            arrow.setMargin(new Insets(0, 0, 0, 0));
            arrow.setToolTipText("Fold or unfold the list");
            arrow.addActionListener(e -> {
                folded = !folded;
                applyState();
            });

            JPanel titleRow = new JPanel(new BorderLayout(Spacing.GAP, 0));
            titleRow.setOpaque(false);
            titleRow.add(check, BorderLayout.WEST);
            titleRow.add(preview, BorderLayout.CENTER);
            JPanel header = new JPanel(new BorderLayout(Spacing.GAP, 0));
            header.setBorder(Spacing.padding(Spacing.GAP / 2, Spacing.GAP, Spacing.GAP / 2, Spacing.GAP));
            header.add(arrow, BorderLayout.WEST);
            header.add(titleRow, BorderLayout.CENTER);

            // the handles of the top rows below the check box
            tree.setBorder(Spacing.padding(0, Spacing.GAP + arrow.getIcon().getIconWidth(), Spacing.GAP / 2,
                    Spacing.GAP));
            tree.setCellRenderer(new EntryRenderer(layerVisible));
            TreeMouseHandler mouse = new TreeMouseHandler();
            tree.addMouseListener(mouse);
            tree.addTreeExpansionListener(new TreeExpansionListener() {
                @Override
                public void treeExpanded(TreeExpansionEvent event) {
                    syncSelection();
                }

                @Override
                public void treeCollapsed(TreeExpansionEvent event) {
                }
            });
            // long texts wrap at the width of the tree, the rows must be measured again when it changes
            tree.addComponentListener(new ComponentAdapter() {
                private int measuredWidth = -1;

                @Override
                public void componentResized(ComponentEvent e) {
                    if (tree.getWidth() != measuredWidth) {
                        measuredWidth = tree.getWidth();
                        remeasureRows();
                    }
                }
            });

            add(header, BorderLayout.NORTH);
            // in the center, a tree in a BoxLayout would not get the width of the section
            add(tree, BorderLayout.CENTER);
            applyState();
        }

        /**
         * Measures the shown rows again, e.g., because the tree got wider and long texts need fewer lines. Rows that
         * are folded away are measured when they are unfolded.
         */
        void remeasureRows() {
            DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
            List<DefaultMutableTreeNode> parents = new ArrayList<>();
            parents.add(root);
            Enumeration<TreePath> expanded = tree.getExpandedDescendants(new TreePath(root));
            if (expanded != null) {
                while (expanded.hasMoreElements()) {
                    Object node = expanded.nextElement().getLastPathComponent();
                    if (node != root) {
                        parents.add((DefaultMutableTreeNode) node);
                    }
                }
            }
            for (DefaultMutableTreeNode parent : parents) {
                int[] children = new int[parent.getChildCount()];
                for (int i = 0; i < children.length; i++) {
                    children[i] = i;
                }
                if (children.length > 0) {
                    treeModel.nodesChanged(parent, children);
                }
            }
        }

        /**
         * @param colors the colors that are shown next to the check box of a layer without structures
         * @param root   the root of the tree
         */
        void show(List<LegendEntry> colors, DefaultMutableTreeNode root) {
            paths.clear();
            treeModel.setRoot(root);
            Enumeration<?> nodes = root.preorderEnumeration();
            while (nodes.hasMoreElements()) {
                TreePath path = new TreePath(((DefaultMutableTreeNode) nodes.nextElement()).getPath());
                FileRegion region = regionOf(path);
                if (region != null) {
                    paths.put(region, path);
                }
            }
            preview.removeAll();
            for (LegendEntry entry : colors) {
                JLabel swatch = new JLabel(new InfoPane.SwatchIcon(entry.getColor(), entry.isOutlined()));
                swatch.setDisabledIcon(new InfoPane.SwatchIcon(dimmed(entry.getColor()), entry.isOutlined()));
                swatch.setToolTipText(entry.getLabel());
                preview.add(swatch);
                preview.add(Box.createHorizontalStrut(2));
            }
            applyState();
        }

        void applyState() {
            check.setSelected(layerVisible.getAsBoolean());
            for (Component swatch : preview.getComponents()) {
                swatch.setEnabled(check.isSelected());
            }
            boolean empty = ((DefaultMutableTreeNode) treeModel.getRoot()).getChildCount() == 0;
            preview.setVisible(colorsOnly && folded);
            tree.setVisible(!folded && !empty);
            arrow.setIcon(new ArrowIcon(folded));
            arrow.setEnabled(!empty);
            tree.repaint(); // the colors are dimmed while the layer is off
            revalidate();
            repaint();
        }

        /**
         * Selects the row of the structure that the views show as selected, if the row is unfolded.
         */
        void syncSelection() {
            TreePath path = paths.get(model.getSelectedRegion());
            if (path != null && tree.isVisible(path)) {
                tree.setSelectionPath(path);
            } else {
                tree.clearSelection();
            }
        }

        private final class TreeMouseHandler extends MouseAdapter {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                    return;
                }
                // a click only marks, a double click unfolds, like the arrow does with one click
                if (!SwingUtilities.isLeftMouseButton(e) || e.getClickCount() != 2) {
                    return;
                }
                TreePath path = pathAt(e);
                if (path == null || treeModel.isLeaf(path.getLastPathComponent())) {
                    return;
                }
                // left of the bounds is the handle, which the tree toggles itself
                Rectangle bounds = tree.getPathBounds(path);
                if (bounds != null && e.getX() >= bounds.x) {
                    if (tree.isExpanded(path)) {
                        tree.collapsePath(path);
                    } else {
                        tree.expandPath(path);
                    }
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showPopup(e);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                FileRegion region = regionOf(pathAt(e));
                if (region == null || !SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                if (e.getClickCount() == 1) {
                    model.showRegion(region, region.getStart());
                } else if (e.getClickCount() == 2 && zoomAction != null) {
                    zoomAction.accept(region);
                }
            }

            private void showPopup(MouseEvent e) {
                TreePath path = pathAt(e);
                FileRegion region = regionOf(path);
                if (region == null) {
                    return;
                }
                tree.setSelectionPath(path);
                RegionMenu.create(tree, model, region, RegionMenu.selectedOffsetIn(model, region), zoomAction,
                        navigator).show(tree, e.getX(), e.getY());
            }

            /**
             * @return the row at the mouse, also left and right of its text, null below the last row
             */
            private TreePath pathAt(MouseEvent e) {
                int row = tree.getClosestRowForLocation(e.getX(), e.getY());
                Rectangle bounds = row < 0 ? null : tree.getRowBounds(row);
                if (bounds == null || e.getY() < bounds.y || e.getY() >= bounds.y + bounds.height) {
                    return null;
                }
                return tree.getPathForRow(row);
            }
        }
    }

    /**
     * The colors of a layer and their structures. Not focusable, so that the keys keep moving the visualization.
     */
    private static final class NavigateTree extends JTree {

        NavigateTree(TreeModel model) {
            super(model);
            setRootVisible(false);
            setShowsRootHandles(true);
            // the mouse handler toggles on a double click, also on the part of the row right of the text
            setToggleClickCount(0);
            // the rows are as high as their text, long texts wrap, see EntryRenderer
            setRowHeight(0);
            setFocusable(false);
            setOpaque(false);
            setSelectionModel(new RegionSelection());
            // the tree never has the focus, the selection shall look like the one of the structures at the pointer
            putClientProperty(FlatClientProperties.STYLE, "selectionInactiveBackground: $Tree.selectionBackground;"
                    + " selectionInactiveForeground: $Tree.selectionForeground");
            ToolTipManager.sharedInstance().registerComponent(this);
        }

        @Override
        public JToolTip createToolTip() {
            // the names come from the file
            JToolTip tip = super.createToolTip();
            PortexSwingUtils.disableHtml(tip);
            return tip;
        }

        @Override
        protected boolean removeDescendantSelectedPaths(TreePath path, boolean includePath) {
            super.removeDescendantSelectedPaths(path, includePath);
            // otherwise folding selects the parent instead, which is another structure than the selected one
            return false;
        }
    }

    /**
     * Selects only rows that stand for a structure, so that a double click on a color unfolds it without marking it.
     */
    private static final class RegionSelection extends DefaultTreeSelectionModel {

        RegionSelection() {
            setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        }

        @Override
        public void setSelectionPaths(TreePath[] paths) {
            if (paths == null || paths.length == 0) {
                super.setSelectionPaths(paths);
                return;
            }
            TreePath[] regionPaths = regionPaths(paths);
            if (regionPaths.length > 0) {
                super.setSelectionPaths(regionPaths);
            }
        }

        @Override
        public void addSelectionPaths(TreePath[] paths) {
            TreePath[] regionPaths = regionPaths(paths);
            if (regionPaths.length > 0) {
                super.addSelectionPaths(regionPaths);
            }
        }

        private static TreePath[] regionPaths(TreePath[] paths) {
            List<TreePath> result = new ArrayList<>();
            if (paths != null) {
                for (TreePath path : paths) {
                    if (regionOf(path) != null) {
                        result.add(path);
                    }
                }
            }
            return result.toArray(new TreePath[0]);
        }
    }

    /**
     * A small triangle, pointing down when the entries are shown and to the right when they are folded.
     */
    private static final class ArrowIcon implements Icon {
        private final boolean folded;

        ArrowIcon(boolean folded) {
            this.folded = folded;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color color = c.isEnabled() ? UIManager.getColor("Label.foreground")
                    : UIManager.getColor("Label.disabledForeground");
            g.setColor(color == null ? Color.gray : color);
            if (folded) {
                g.fillPolygon(new int[]{x + 3, x + 3, x + 8}, new int[]{y + 2, y + 10, y + 6}, 3);
            } else {
                g.fillPolygon(new int[]{x + 1, x + 10, x + 5}, new int[]{y + 3, y + 3, y + 8}, 3);
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

    /**
     * A color as swatch and label, a structure as name. The number of structures of a color, or the range of a
     * structure, follow in a muted color. Texts that are wider than the tree wrap, e.g., descriptions of anomalies.
     */
    private static final class EntryRenderer extends DefaultTreeCellRenderer {

        /** the place of the swatch of a structure without color, so that the names stay in line */
        private static final Icon NO_COLOR = new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
            }

            @Override
            public int getIconWidth() {
                return 16;
            }

            @Override
            public int getIconHeight() {
                return 16;
            }
        };

        /** the narrowest text that a row wraps to, also in a very narrow tree */
        private static final int MIN_TEXT_WIDTH = 60;

        private final BooleanSupplier layerVisible;
        private String detail = "";
        private boolean selected;
        // the lines of a text that does not fit into the tree, null if it does
        private List<String> lines;
        // true if the detail does not fit behind the last line and has a line of its own
        private boolean detailOwnLine;

        EntryRenderer(BooleanSupplier layerVisible) {
            this.layerVisible = layerVisible;
            // labels can come from the file, e.g., names of resource types and sections
            PortexSwingUtils.disableHtml(this);
        }

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, false);
            this.selected = selected;
            boolean layerOn = layerVisible.getAsBoolean();
            boolean muted = !layerOn;
            Object item = value instanceof DefaultMutableTreeNode ? ((DefaultMutableTreeNode) value).getUserObject()
                    : value;
            setIcon(null);
            setToolTipText(null);
            detail = "";
            if (item instanceof Category) {
                LegendEntry entry = ((Category) item).getEntry();
                List<FileRegion> regions = ((Category) item).getRegions();
                setText(entry.getLabel());
                Color color = layerOn ? entry.getColor() : dimmed(entry.getColor());
                setIcon(new InfoPane.SwatchIcon(color, entry.isOutlined()));
                if (regions.size() == 1) {
                    describe(regions.get(0));
                } else if (regions.size() > 1) {
                    detail = String.valueOf(regions.size());
                    setToolTipText(entry.getLabel() + ", " + regions.size() + " structures");
                }
            } else if (item instanceof Structure) {
                Structure structure = (Structure) item;
                setText(structure.getLabel());
                Color color = structure.getColor();
                setIcon(color == null ? NO_COLOR
                        : new InfoPane.SwatchIcon(layerOn ? color : dimmed(color), structure.isOutlined()));
                if (structure.getRegion() != null) {
                    describe(structure.getRegion());
                } else {
                    detail = "no bytes in the file";
                    setToolTipText("The section has no bytes in the file, e.g., because it holds uninitialized data");
                }
            } else if (item instanceof FileRegion) {
                FileRegion region = (FileRegion) item;
                setText(region.getName());
                describe(region);
            } else {
                setText(String.valueOf(item)); // the number of structures that are not listed
                muted = true;
            }
            if (muted && !selected) {
                setForeground(mutedForeground());
            }
            layoutLines(tree, value);
            return this;
        }

        private void describe(FileRegion region) {
            detail = StructureMap.hexRange(region.getStart(), region.getEnd());
            setToolTipText(region.getPath() + " " + detail);
        }

        /**
         * Wraps the text if the row would be wider than the room in the tree.
         */
        private void layoutLines(JTree tree, Object value) {
            lines = null;
            detailOwnLine = false;
            int available = availableWidth(tree, value);
            FontMetrics metrics = getFontMetrics(getFont());
            int unwrapped = textX() + metrics.stringWidth(getText()) + getInsets().right
                    + (detail.isEmpty() ? 0 : DETAIL_GAP + metrics.stringWidth(detail));
            if (available <= 0 || unwrapped <= available) {
                return;
            }
            int textWidth = Math.max(MIN_TEXT_WIDTH, available - textX() - getInsets().right);
            lines = wrap(getText(), metrics, textWidth);
            if (!detail.isEmpty()) {
                int last = metrics.stringWidth(lines.get(lines.size() - 1));
                detailOwnLine = last + DETAIL_GAP + metrics.stringWidth(detail) > textWidth;
            }
        }

        /**
         * @return the room for the row in the tree, 0 if the tree has no size yet
         */
        private static int availableWidth(JTree tree, Object value) {
            if (tree == null || tree.getWidth() <= 0 || !(value instanceof DefaultMutableTreeNode)) {
                return 0;
            }
            int indent = 0;
            if (tree.getUI() instanceof BasicTreeUI) {
                BasicTreeUI ui = (BasicTreeUI) tree.getUI();
                indent = ui.getLeftChildIndent() + ui.getRightChildIndent();
            }
            // the root is hidden, its children are at level 1 right of the column of the handles
            int rowX = indent * ((DefaultMutableTreeNode) value).getLevel();
            Insets insets = tree.getInsets();
            return tree.getWidth() - insets.left - insets.right - rowX;
        }

        /**
         * @return the text in lines that are at most as wide as given, broken at spaces; a word that is wider than a
         *         line is broken where the line is full
         */
        static List<String> wrap(String text, FontMetrics metrics, int width) {
            List<String> lines = new ArrayList<>();
            String line = "";
            for (String word : text.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (metrics.stringWidth(candidate) <= width) {
                    line = candidate;
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line);
                }
                line = word;
                while (line.length() > 1 && metrics.stringWidth(line) > width) {
                    int fit = 1;
                    while (fit < line.length() - 1 && metrics.stringWidth(line.substring(0, fit + 1)) <= width) {
                        fit++;
                    }
                    lines.add(line.substring(0, fit));
                    line = line.substring(fit);
                }
            }
            if (!line.isEmpty() || lines.isEmpty()) {
                lines.add(line);
            }
            return lines;
        }

        /**
         * @return where the text starts, right of the icon
         */
        private int textX() {
            Icon icon = getIcon();
            return getInsets().left + (icon == null ? 0 : icon.getIconWidth() + getIconTextGap());
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            if (size == null) {
                return null;
            }
            FontMetrics metrics = getFontMetrics(getFont());
            if (lines == null) {
                if (!detail.isEmpty()) {
                    size.width += DETAIL_GAP + metrics.stringWidth(detail);
                }
                size.height = Math.max(size.height, ENTRY_HEIGHT);
                return size;
            }
            int width = 0;
            for (String line : lines) {
                width = Math.max(width, metrics.stringWidth(line));
            }
            if (!detail.isEmpty()) {
                int last = metrics.stringWidth(lines.get(lines.size() - 1));
                width = Math.max(width, detailOwnLine ? metrics.stringWidth(detail)
                        : last + DETAIL_GAP + metrics.stringWidth(detail));
            }
            int lineCount = lines.size() + (detailOwnLine ? 1 : 0);
            // the same room above and below the text as in a row of one line
            return new Dimension(textX() + width + getInsets().right,
                    lineCount * metrics.getHeight() + ENTRY_HEIGHT - metrics.getHeight());
        }

        @Override
        protected void paintComponent(Graphics g) {
            FontMetrics metrics = getFontMetrics(getFont());
            Graphics2D g2 = (Graphics2D) g;
            if (lines == null) {
                super.paintComponent(g);
                if (!detail.isEmpty()) {
                    Insets insets = getInsets();
                    int x = textX() + metrics.stringWidth(getText()) + DETAIL_GAP;
                    int y = insets.top + (getHeight() - insets.top - insets.bottom - metrics.getHeight()) / 2
                            + metrics.getAscent();
                    // the foreground is the selection color in selected rows
                    g2.setColor(selected ? getForeground() : mutedForeground());
                    BasicGraphicsUtils.drawString(this, g2, detail, x, y);
                }
                return;
            }
            // several lines: the label paints only one, so the lines are painted here, the icon at the first one
            int top = (ENTRY_HEIGHT - metrics.getHeight()) / 2;
            Icon icon = getIcon();
            if (icon != null) {
                icon.paintIcon(this, g, getInsets().left, top + (metrics.getHeight() - icon.getIconHeight()) / 2);
            }
            int x = textX();
            int y = top + metrics.getAscent();
            g2.setColor(getForeground());
            for (String line : lines) {
                BasicGraphicsUtils.drawString(this, g2, line, x, y);
                y += metrics.getHeight();
            }
            if (!detail.isEmpty()) {
                int detailX = detailOwnLine ? x : x + metrics.stringWidth(lines.get(lines.size() - 1)) + DETAIL_GAP;
                int detailY = detailOwnLine ? y : y - metrics.getHeight();
                g2.setColor(selected ? getForeground() : mutedForeground());
                BasicGraphicsUtils.drawString(this, g2, detail, detailX, detailY);
            }
        }
    }
}
