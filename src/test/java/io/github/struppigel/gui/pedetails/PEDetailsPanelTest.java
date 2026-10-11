package io.github.struppigel.gui.pedetails;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.PEComponentTree;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.testsupport.TestFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.struppigel.testsupport.Swing.*;
import static io.github.struppigel.testsupport.TestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Selects nodes in the tree on the left and checks what the details panel shows, like a user clicking through the
 * GUI, but without a window.
 */
class PEDetailsPanelTest {

    private static final List<String> FIELD_TABLE_HEADERS = Arrays.asList("Description", "Value", "File offset");

    @TempDir
    Path tempDir;

    private final List<Throwable> edtErrors = Collections.synchronizedList(new ArrayList<>());
    private Thread.UncaughtExceptionHandler previousHandler;
    private PEDetailsPanel details;
    private JTree tree;

    static List<String> allPE() {
        return TestFiles.ALL_PE;
    }

    @BeforeEach
    void collectEdtErrors() {
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, e) -> edtErrors.add(e));
    }

    @AfterEach
    void cleanUp() throws Exception {
        WorkerKiller.getInstance().cancelAndDeleteWorkers();
        onEdt(() -> {}); // let pending events run
        Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        assertTrue(edtErrors.isEmpty(), "exceptions on the event dispatch thread: " + edtErrors);
    }

    @Test
    void peFormatShowsSummary() throws Exception {
        load(NATIVE_X86);
        select("PE Format");

        assertEquals("DESCRIPTION", details.getVisibleCardName());
        assertTrue(visibleText().contains("Sections: "), visibleText());
    }

    @Test
    void coffFileHeaderShowsFieldTable() throws Exception {
        load(NATIVE_X86);
        select("COFF File Header");

        assertEquals("TABLE", details.getVisibleCardName());
        JTable table = singleVisibleTable();
        assertEquals(FIELD_TABLE_HEADERS, columnNames(table));
        assertTrue(table.getRowCount() > 0);
        assertTrue(visibleText().contains("Machine type: "), visibleText());
    }

    @Test
    void dosStubShowsFieldTable() throws Exception {
        load(NATIVE_X86);
        select("MS DOS Stub");

        assertEquals("TABLE", details.getVisibleCardName());
        assertEquals(FIELD_TABLE_HEADERS, columnNames(singleVisibleTable()));
    }

    @Test
    void importsShowImportTable() throws Exception {
        FullPEData data = load(NATIVE_X86);
        select("Imports");

        assertEquals("TABLE", details.getVisibleCardName());
        JTable table = singleVisibleTable();
        assertEquals(Arrays.asList("DLL", "Category", "Name", "Description", "RVA", "Hint"), columnNames(table));
        assertEquals(data.getImportTableEntries().size(), table.getRowCount());
    }

    @Test
    void hashesShowReportAndSectionHashes() throws Exception {
        load(NATIVE_X86);
        select("Hashes");

        assertEquals("TABLE", details.getVisibleCardName());
        assertEquals(Arrays.asList("Section", "Type", "Hash value"), columnNames(singleVisibleTable()));
        assertTrue(visibleText().contains("MD5: "), visibleText());
    }

    @Test
    void overlayShowsDescriptionAndButtons() throws Exception {
        load(OVERLAY);
        select("Overlay");

        assertEquals("DESCBUTTON", details.getVisibleCardName());
        assertTrue(visibleText().contains("Offset: "), visibleText());
        List<String> buttons = onEdtGet(() -> findVisible(details, JButton.class).stream()
                .map(AbstractButton::getText).collect(Collectors.toList()));
        assertTrue(buttons.containsAll(Arrays.asList("Dump overlay", "Remove overlay")), buttons.toString());
    }

    @Test
    void debugShowsOneTabPerEntry() throws Exception {
        FullPEData data = load(DEBUG_INFO);
        select("Debug");

        assertEquals("TABBED", details.getVisibleCardName());
        JTabbedPane tabs = onEdtGet(() -> findVisible(details, JTabbedPane.class).get(0));
        assertEquals(data.getDebugTableEntries().size(), tabs.getTabCount());
    }

    @Test
    void sectionTableShowsSections() throws Exception {
        load(NATIVE_X86);
        select("Section Table");

        assertEquals("SECTIONS", details.getVisibleCardName());
    }

    @Test
    void iconsVisualizationAndSignaturesHaveOwnViews() throws Exception {
        load(ICONS_X64);

        select("Icons");
        assertEquals("ICONS", details.getVisibleCardName());
        select("Visualization");
        assertEquals("VISUALIZATION", details.getVisibleCardName());
        select("Signatures");
        assertEquals("SIGNATURES", details.getVisibleCardName());
    }

    @Test
    void clrTableShowsRows() throws Exception {
        FullPEData data = load(DOTNET);
        String tableName = data.getClrTables().keySet().stream().sorted().findFirst().get();
        select(tableName);

        assertEquals("TABLE", details.getVisibleCardName());
        JTable table = singleVisibleTable();
        List<String> headers = columnNames(table);
        assertEquals("Row", headers.get(0));
        assertEquals("File offset", headers.get(headers.size() - 1));
        assertEquals(data.getClrTables().get(tableName).size(), table.getRowCount());
    }

    /**
     * Clicks every node of the tree.
     */
    @ParameterizedTest
    @MethodSource("allPE")
    void everyNodeCanBeSelected(String sample) throws Exception {
        load(sample);
        List<TreePath> paths = onEdtGet(() -> Swing.allPaths(tree));

        for (TreePath path : paths) {
            onEdt(() -> tree.setSelectionPath(path));
            assertNotNull(details.getVisibleCardName(), path.toString());
        }
    }

    private FullPEData load(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);
        PortexSettings settings = new PortexSettings(tempDir.resolve("settings.ini").toFile());
        onEdt(() -> {
            FileContentPreviewPanel preview = new FileContentPreviewPanel();
            details = new PEDetailsPanel(new JPanel(), null, settings, preview);
            // the visualizer needs a size
            Swing.layout(details, 800, 600);
            PEComponentTree componentTree = new PEComponentTree(details);
            // same order as MainFrame.setPeData
            details.setPeData(data);
            componentTree.setPeData(data);
            preview.setPeData(data);
            tree = Swing.findAll(componentTree, JTree.class).get(0);
        });
        return data;
    }

    private void select(String label) throws Exception {
        onEdt(() -> {
            TreePath path = Swing.findPath(tree, label);
            assertNotNull(path, "no tree node " + label);
            tree.setSelectionPath(path);
        });
    }

    private JTable singleVisibleTable() throws Exception {
        List<JTable> tables = onEdtGet(() -> findVisible(details, JTable.class));
        assertEquals(1, tables.size());
        return tables.get(0);
    }

    private String visibleText() throws Exception {
        return onEdtGet(() -> findVisible(details, JTextArea.class).stream()
                .map(JTextArea::getText).collect(Collectors.joining("\n")));
    }
}
