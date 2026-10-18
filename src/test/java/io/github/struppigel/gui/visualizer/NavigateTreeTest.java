package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.layout.StructureMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static io.github.struppigel.testsupport.Swing.findAll;
import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.TestFiles.NATIVE_X86;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The trees of the legend: all structures of the file in the structure layer, the markers by color.
 */
class NavigateTreeTest {

    private VisualizationModel model;
    private LegendPanel legend;
    private FullPEData data;
    private final List<FileRegion> zoomed = new ArrayList<>();

    @AfterEach
    void cleanUp() throws Exception {
        WorkerKiller.getInstance().cancelAndDeleteWorkers();
        onEdt(() -> {
            if (model != null) {
                model.dispose();
            }
        });
    }

    private void load() throws Exception {
        data = TestPEData.load(NATIVE_X86);
        onEdt(() -> {
            model = new VisualizationModel();
            legend = new LegendPanel(model);
            legend.setZoomAction(zoomed::add);
            model.loadSynchronously(data);
        });
    }

    private StructureMap map() {
        return model.getVisualization().getStructureMap();
    }

    /** the trees are in the order of the sections: byte plot, entropy, structure, markers */
    private JTree structureTree() {
        return findAll(legend, JTree.class).get(2);
    }

    private JTree markersTree() {
        return findAll(legend, JTree.class).get(3);
    }

    private static DefaultMutableTreeNode root(JTree tree) {
        return (DefaultMutableTreeNode) tree.getModel().getRoot();
    }

    private static TreePath pathOf(DefaultMutableTreeNode node) {
        return new TreePath(node.getPath());
    }

    private static FileRegion regionAt(DefaultMutableTreeNode node) {
        return LegendPanel.regionOf(pathOf(node));
    }

    private static List<DefaultMutableTreeNode> children(DefaultMutableTreeNode parent) {
        List<DefaultMutableTreeNode> children = new ArrayList<>();
        for (int i = 0; i < parent.getChildCount(); i++) {
            children.add((DefaultMutableTreeNode) parent.getChildAt(i));
        }
        return children;
    }

    /**
     * @return the first child of the node that stands for a structure of the type
     */
    private static DefaultMutableTreeNode child(DefaultMutableTreeNode parent, RegionType type) {
        List<String> seen = new ArrayList<>();
        for (DefaultMutableTreeNode node : children(parent)) {
            FileRegion region = regionAt(node);
            if (region != null && region.getType() == type) {
                return node;
            }
            seen.add(node + (region == null ? "" : " " + region.getType()));
        }
        throw new AssertionError("no " + type + " in " + seen);
    }

    private static List<RegionType> childTypes(DefaultMutableTreeNode parent) {
        List<RegionType> types = new ArrayList<>();
        for (DefaultMutableTreeNode node : children(parent)) {
            types.add(regionAt(node).getType());
        }
        return types;
    }

    private static void mouse(JTree tree, int id, TreePath path, int clickCount) {
        Rectangle bounds = tree.getPathBounds(path);
        assertNotNull(bounds, "the row is shown: " + path);
        tree.dispatchEvent(new MouseEvent(tree, id, 0, 0, bounds.x + 2, bounds.y + bounds.height / 2,
                clickCount, false, MouseEvent.BUTTON1));
    }

    private static void press(JTree tree, TreePath path, int clickCount) {
        // the tree UI asks the toolkit for the shortcut key on presses, which throws in headless mode; the legend's
        // own handler does the toggling, the UI only selects, which the selection model checks anyway
        for (MouseListener listener : tree.getMouseListeners()) {
            if (listener.getClass().getName().startsWith("javax.swing.plaf.")) {
                tree.removeMouseListener(listener);
            }
        }
        mouse(tree, MouseEvent.MOUSE_PRESSED, path, clickCount);
    }

    private static void click(JTree tree, TreePath path, int clickCount) {
        mouse(tree, MouseEvent.MOUSE_CLICKED, path, clickCount);
    }

    @Test
    void theTreesStartWithTheirTopRowsAndByteplotAndEntropyFolded() throws Exception {
        load();
        onEdt(() -> {
            List<JTree> trees = findAll(legend, JTree.class);
            assertEquals(4, trees.size());
            assertFalse(trees.get(0).isVisible(), "byte plot");
            assertFalse(trees.get(1).isVisible(), "entropy");
            for (JTree tree : List.of(structureTree(), markersTree())) {
                assertTrue(tree.isVisible());
                assertTrue(root(tree).getChildCount() > 0);
                assertEquals(root(tree).getChildCount(), tree.getRowCount(), "only the top rows are shown");
            }
        });
    }

    @Test
    void theHeadersAreAtTheTopAndTheDataDirectoryIsInTheOptionalHeader() throws Exception {
        load();
        onEdt(() -> {
            DefaultMutableTreeNode root = root(structureTree());
            List<RegionType> top = childTypes(root);
            assertFalse(top.contains(RegionType.HEADERS), "the headers are no node of their own: " + top);
            assertTrue(top.containsAll(List.of(RegionType.MSDOS_HEADER, RegionType.COFF_FILE_HEADER,
                    RegionType.OPTIONAL_HEADER, RegionType.SECTION_TABLE, RegionType.IMPORT_SECTION)), "" + top);

            DefaultMutableTreeNode optional = child(root, RegionType.OPTIONAL_HEADER);
            assertEquals(List.of(RegionType.STANDARD_FIELDS, RegionType.WINDOWS_FIELDS, RegionType.DATA_DIRECTORY),
                    childTypes(optional));
            DefaultMutableTreeNode directory = child(optional, RegionType.DATA_DIRECTORY);
            FileRegion directoryRegion = map().getRegions(RegionType.DATA_DIRECTORY).get(0);
            assertSame(directoryRegion, regionAt(directory));
            assertEquals(directoryRegion.getChildren().size(), directory.getChildCount());
            assertEquals(Collections.nCopies(directory.getChildCount(), RegionType.DATA_DIRECTORY_ENTRY),
                    childTypes(directory));
        });
    }

    @Test
    void everyStructureExceptTheMarkersIsListedOnce() throws Exception {
        load();
        onEdt(() -> {
            List<FileRegion> listed = new ArrayList<>();
            Enumeration<?> nodes = root(structureTree()).preorderEnumeration();
            while (nodes.hasMoreElements()) {
                FileRegion region = LegendPanel.regionOf(pathOf((DefaultMutableTreeNode) nodes.nextElement()));
                if (region != null) {
                    listed.add(region);
                }
            }
            Set<FileRegion> unique = Collections.newSetFromMap(new IdentityHashMap<>());
            unique.addAll(listed);
            assertEquals(unique.size(), listed.size(), "no structure is listed twice");

            List<FileRegion> missing = new ArrayList<>();
            for (FileRegion region : map().getRegions()) {
                RegionType type = region.getType();
                boolean expected = type != RegionType.HEADERS && type != RegionType.ANOMALY
                        && type != RegionType.SIGNATURE_MATCH;
                assertEquals(expected, unique.contains(region), region.getPath());
                if (expected && !unique.contains(region)) {
                    missing.add(region);
                }
            }
            assertTrue(missing.isEmpty(), "missing: " + missing);
        });
    }

    @Test
    void theSectionsAreBelowTheSectionTableWithTheirNumbersAndHeaders() throws Exception {
        load();
        onEdt(() -> {
            DefaultMutableTreeNode root = root(structureTree());
            assertFalse(childTypes(root).contains(RegionType.SECTION), "no section at the top");
            DefaultMutableTreeNode table = child(root, RegionType.SECTION_TABLE);
            List<FileRegion> headers = map().getRegions(RegionType.SECTION_HEADER);
            assertEquals(headers.size(), table.getChildCount(), "one row per section");

            for (DefaultMutableTreeNode node : children(table)) {
                // every section has its header as first row, also one without bytes in the file
                DefaultMutableTreeNode header = (DefaultMutableTreeNode) node.getChildAt(0);
                assertEquals("Section Header", header.toString(), node.toString());
                FileRegion headerRegion = regionAt(header);
                assertEquals(RegionType.SECTION_HEADER, headerRegion.getType());
                assertTrue(header.getChildCount() > 0, "the fields of the header");
                String number = headerRegion.getAttributes().get(FileRegion.ATTR_SECTION_NUMBER);
                String name = headerRegion.getAttributes().get(FileRegion.ATTR_SECTION_NAME);
                assertEquals(number + ". " + name, node.toString());
                FileRegion section = regionAt(node);
                if (section != null) {
                    assertEquals(RegionType.SECTION, section.getType());
                    assertEquals(number, section.getAttributes().get(FileRegion.ATTR_SECTION_NUMBER));
                }
            }

            // .textbss has no bytes in the file: its row stands for no structure
            DefaultMutableTreeNode textbss = (DefaultMutableTreeNode) table.getChildAt(0);
            assertEquals("1. .textbss", textbss.toString());
            assertNull(regionAt(textbss));
            JTree tree = structureTree();
            JLabel rendered = (JLabel) tree.getCellRenderer().getTreeCellRendererComponent(tree, textbss, false,
                    false, false, 0, false);
            assertTrue(rendered.getToolTipText().contains("no bytes in the file"), rendered.getToolTipText());
            assertEquals("2. .text", table.getChildAt(1).toString());
            assertEquals(RegionType.SECTION, regionAt((DefaultMutableTreeNode) table.getChildAt(1)).getType());
        });
    }

    @Test
    void structuresBeyondTheLimitAreCounted() throws Exception {
        load();
        onEdt(() -> {
            DefaultMutableTreeNode root = LegendPanel.structureRoot(map(), model.getVisualization().getColorScheme(),
                    2);
            int top = root(structureTree()).getChildCount();
            assertEquals(3, root.getChildCount());
            DefaultMutableTreeNode more = (DefaultMutableTreeNode) root.getChildAt(2);
            assertEquals((top - 2) + " more are not listed", more.getUserObject());
            assertNull(regionAt(more), "the count is no structure");

            DefaultMutableTreeNode windows = child(child(root(structureTree()), RegionType.OPTIONAL_HEADER),
                    RegionType.WINDOWS_FIELDS);
            FileRegion windowsRegion = regionAt(windows);
            DefaultMutableTreeNode limited = LegendPanel.structureRoot(map(),
                    model.getVisualization().getColorScheme(), windowsRegion.getChildren().size() - 1);
            DefaultMutableTreeNode limitedWindows = child(child(limited, RegionType.OPTIONAL_HEADER),
                    RegionType.WINDOWS_FIELDS);
            assertEquals(windowsRegion.getChildren().size(), limitedWindows.getChildCount());
            assertEquals("1 more are not listed", ((DefaultMutableTreeNode) limitedWindows.getLastChild())
                    .getUserObject());
        });
    }

    @Test
    void theMarkersAreListedByColorAndOffset() throws Exception {
        load();
        onEdt(() -> {
            DefaultMutableTreeNode category = null;
            for (DefaultMutableTreeNode node : children(root(markersTree()))) {
                if (node.getChildCount() > 1) {
                    category = node;
                }
            }
            assertNotNull(category, "a color with several markers");
            LegendPanel.Category color = (LegendPanel.Category) category.getUserObject();
            assertEquals(color.getRegions().size(), category.getChildCount());
            for (int i = 1; i < category.getChildCount(); i++) {
                FileRegion previous = (FileRegion) ((DefaultMutableTreeNode) category.getChildAt(i - 1)).getUserObject();
                FileRegion next = (FileRegion) ((DefaultMutableTreeNode) category.getChildAt(i)).getUserObject();
                assertTrue(previous.getStart() <= next.getStart(), previous + " before " + next);
            }

            DefaultMutableTreeNode node = LegendPanel.categoryNode(color.getEntry(), 1);
            assertEquals((color.getRegions().size() - 1) + " more are not listed",
                    ((DefaultMutableTreeNode) node.getChildAt(1)).getUserObject());
        });
    }

    @Test
    void aDoubleClickUnfoldsAStructureAndOneClickDoesNot() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = structureTree();
            TreePath path = pathOf(child(root(tree), RegionType.OPTIONAL_HEADER));

            press(tree, path, 1);
            assertFalse(tree.isExpanded(path), "one click");
            press(tree, path, 2);
            assertTrue(tree.isExpanded(path), "the second click of a double click");
            press(tree, path, 1);
            assertTrue(tree.isExpanded(path), "one click does not fold either");
            press(tree, path, 2);
            assertFalse(tree.isExpanded(path));
        });
    }

    @Test
    void clickingAStructureMarksItAndADoubleClickZooms() throws Exception {
        load();
        onEdt(() -> {
            List<Long> requested = new ArrayList<>();
            model.addListener(new VisualizationModel.Listener() {
                @Override
                public void viewRequested(long offset, boolean onlyIfInvisible) {
                    assertTrue(onlyIfInvisible, "a visible structure stays where it is");
                    requested.add(offset);
                }
            });
            JTree tree = structureTree();
            DefaultMutableTreeNode optional = child(root(tree), RegionType.OPTIONAL_HEADER);
            tree.expandPath(pathOf(optional));
            DefaultMutableTreeNode directory = child(optional, RegionType.DATA_DIRECTORY);
            FileRegion region = regionAt(directory);

            click(tree, pathOf(directory), 1);
            assertSame(region, model.getSelectedRegion());
            assertEquals(region.getStart(), model.getSelectedOffset());
            assertEquals(List.of(region.getStart()), requested);
            assertEquals(pathOf(directory), tree.getSelectionPath(), "the row shows the selection");

            click(tree, pathOf(directory), 2);
            assertEquals(List.of(region), zoomed);
        });
    }

    @Test
    void oneClickOnAStructureWithChildrenOnlyMarksIt() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = structureTree();
            DefaultMutableTreeNode table = child(root(tree), RegionType.SECTION_TABLE);

            press(tree, pathOf(table), 1);
            click(tree, pathOf(table), 1);

            assertFalse(tree.isExpanded(pathOf(table)));
            assertSame(regionAt(table), model.getSelectedRegion());
            assertTrue(zoomed.isEmpty());
        });
    }

    @Test
    void aDoubleClickOnAStructureWithChildrenUnfoldsItAndZooms() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = structureTree();
            DefaultMutableTreeNode table = child(root(tree), RegionType.SECTION_TABLE);

            press(tree, pathOf(table), 1);
            click(tree, pathOf(table), 1);
            press(tree, pathOf(table), 2);
            click(tree, pathOf(table), 2);

            assertTrue(tree.isExpanded(pathOf(table)));
            assertEquals(List.of(regionAt(table)), zoomed);
        });
    }

    @Test
    void clickingAColorOfTheMarkersKeepsTheSelection() throws Exception {
        load();
        onEdt(() -> {
            DefaultMutableTreeNode optional = child(root(structureTree()), RegionType.OPTIONAL_HEADER);
            FileRegion selected = regionAt(optional);
            model.selectRegion(selected, selected.getStart());
            JTree tree = markersTree();
            DefaultMutableTreeNode category = null;
            for (DefaultMutableTreeNode node : children(root(tree))) {
                if (node.getChildCount() > 1) {
                    category = node;
                }
            }
            assertNotNull(category);

            press(tree, pathOf(category), 1);
            click(tree, pathOf(category), 1);
            tree.setSelectionPath(pathOf(category)); // what the tree UI does on a press
            assertFalse(tree.isExpanded(pathOf(category)), "one click");
            press(tree, pathOf(category), 2);
            click(tree, pathOf(category), 2);
            tree.setSelectionPath(pathOf(category));

            assertTrue(tree.isExpanded(pathOf(category)), "a double click");
            assertSame(selected, model.getSelectedRegion());
            assertFalse(tree.isPathSelected(pathOf(category)), "a color is not marked");
            assertTrue(zoomed.isEmpty());
        });
    }

    @Test
    void unfoldedStructuresStayWhenALayerIsSwitchedAndFoldWhenAFileIsLoaded() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = structureTree();
            TreePath optional = pathOf(child(root(tree), RegionType.OPTIONAL_HEADER));
            tree.expandPath(optional);
            Object root = tree.getModel().getRoot();

            model.setStructureVisible(false);
            model.setStructureVisible(true);
            model.setByteplotVisible(false);

            assertSame(root, tree.getModel().getRoot());
            assertTrue(tree.isExpanded(optional));

            model.loadSynchronously(data);
            assertNotSame(root, tree.getModel().getRoot());
            assertEquals(root(tree).getChildCount(), tree.getRowCount(), "all rows are folded");
        });
    }

    @Test
    void theSelectionOfTheViewsIsShownWhenItsRowIsUnfolded() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = structureTree();
            DefaultMutableTreeNode optional = child(root(tree), RegionType.OPTIONAL_HEADER);
            TreePath directory = pathOf(child(optional, RegionType.DATA_DIRECTORY));
            FileRegion region = LegendPanel.regionOf(directory);

            model.selectRegion(region, region.getStart());
            assertNull(tree.getSelectionPath(), "the optional header is folded");

            tree.expandPath(pathOf(optional));
            assertEquals(directory, tree.getSelectionPath(), "unfolding shows the selection");

            model.clearSelection();
            assertNull(tree.getSelectionPath());

            model.selectRegion(region, region.getStart());
            assertEquals(directory, tree.getSelectionPath());
            tree.collapsePath(pathOf(optional));
            assertNull(tree.getSelectionPath(), "the folded parent is not marked instead");
        });
    }

    @Test
    void structuresWithoutColorAreInLineWithTheOthers() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = structureTree();
            DefaultMutableTreeNode optional = child(root(tree), RegionType.OPTIONAL_HEADER);
            DefaultMutableTreeNode directory = child(optional, RegionType.DATA_DIRECTORY);

            JLabel colored = (JLabel) tree.getCellRenderer().getTreeCellRendererComponent(tree, optional, false,
                    false, false, 0, false);
            assertTrue(colored.getIcon() instanceof InfoPane.SwatchIcon);
            int width = colored.getIcon().getIconWidth();
            JLabel plain = (JLabel) tree.getCellRenderer().getTreeCellRendererComponent(tree, directory, false,
                    false, false, 0, false);
            assertFalse(plain.getIcon() instanceof InfoPane.SwatchIcon, "the data directory has no color");
            assertEquals(width, plain.getIcon().getIconWidth());
        });
    }

    @Test
    void longDescriptionsWrapAtTheWidthOfTheTree() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = markersTree();
            io.github.struppigel.testsupport.Swing.layout(legend, 300, 3000);
            // without a window, Swing does not deliver the resize, which measures the rows for the width
            tree.dispatchEvent(new ComponentEvent(tree, ComponentEvent.COMPONENT_RESIZED));
            DefaultMutableTreeNode anomalies = null;
            for (DefaultMutableTreeNode node : children(root(tree))) {
                if (node.toString().startsWith("Anomal")) {
                    anomalies = node;
                }
            }
            assertNotNull(anomalies, "the anomalies in " + children(root(tree)));
            int oneLine = tree.getPathBounds(pathOf(anomalies)).height;
            tree.expandPath(pathOf(anomalies));

            int right = tree.getWidth() - tree.getInsets().right;
            int wrapped = 0;
            for (DefaultMutableTreeNode node : children(anomalies)) {
                Rectangle bounds = tree.getPathBounds(pathOf(node));
                assertTrue(bounds.x + bounds.width <= right, node + ": " + bounds + " ends right of " + right);
                assertTrue(bounds.height >= oneLine, node + ": " + bounds);
                if (bounds.height > oneLine) {
                    wrapped++;
                }
            }
            assertTrue(wrapped > 0, "a long description takes several lines");

            // in a wide tree, the rows take one line again
            io.github.struppigel.testsupport.Swing.layout(legend, 3000, 3000);
            tree.dispatchEvent(new ComponentEvent(tree, ComponentEvent.COMPONENT_RESIZED));
            for (DefaultMutableTreeNode node : children(anomalies)) {
                assertEquals(oneLine, tree.getPathBounds(pathOf(node)).height, node.toString());
            }
        });
    }

    @Test
    void theTooltipOfAStructureShowsItsPath() throws Exception {
        load();
        onEdt(() -> {
            JTree tree = structureTree();
            TreePath path = pathOf(child(root(tree), RegionType.PE_SIGNATURE));
            FileRegion region = LegendPanel.regionOf(path);
            Rectangle bounds = tree.getPathBounds(path);

            String tip = tree.getToolTipText(new MouseEvent(tree, MouseEvent.MOUSE_MOVED, 0, 0, bounds.x + 2,
                    bounds.y + bounds.height / 2, 0, false));

            assertNotNull(tip);
            assertTrue(tip.startsWith(region.getPath()), tip);
        });
    }
}
