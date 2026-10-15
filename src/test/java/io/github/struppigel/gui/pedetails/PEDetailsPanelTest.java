package io.github.struppigel.gui.pedetails;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.PEComponentTree;
import io.github.struppigel.gui.utils.InfoCard;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.gui.visualizer.DetailCanvas;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.visualizer.render.Track;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.testsupport.TestFiles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseEvent;
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
    private FileContentPreviewPanel preview;
    private PEComponentTree componentTree;
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
        onEdt(() -> {
            if (details != null) {
                details.getVisualizationModel().dispose();
            }
        });
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
        assertEquals(Arrays.asList("DLL", "Category", "Name", "Description", "RVA", "Hint", "Offset"),
                columnNames(table));
        assertEquals(data.getImportTableEntries().size(), table.getRowCount());
    }

    @Test
    void selectingAnImportRowMarksOnlyTheDataOfTheFunction() throws Exception {
        load(NATIVE_X86);
        select("Imports");
        JTable table = singleVisibleTable();
        int row = table.getRowCount() - 1;
        long offset = (Long) table.getValueAt(row, 6);

        onEdt(() -> table.setRowSelectionInterval(row, row));

        // lookup table entry, hint and name, address table slot, not the whole lookup table
        FileRegion region = onEdtGet(() -> details.getVisualizationModel().getSelectedRegion());
        assertNotNull(region);
        assertTrue(region.getLocations().size() <= 3, region.toString());
        assertTrue(region.getLocations().stream().anyMatch(l -> l.from() == offset), region.toString());
        assertEquals(offset, (long) onEdtGet(() -> preview.getCurrentOffset()));
        waitFor(() -> preview.getText().startsWith("Offset: 0x" + Long.toHexString(offset)));
        assertTrue(onEdtGet(() -> preview.highlightCount()) > 0);
    }

    @Test
    void selectingAnAnomalyRowShowsItsLocation() throws Exception {
        load(NATIVE_X86);
        select("Anomalies");
        JTable table = singleVisibleTable();
        assertEquals("Offset", columnNames(table).get(4));
        int row = -1;
        for (int i = 0; i < table.getRowCount() && row < 0; i++) {
            if (table.getValueAt(i, 4) instanceof Long) {
                row = i;
            }
        }
        Assumptions.assumeTrue(row >= 0, "the sample has no anomaly with a location");
        int selectedRow = row;
        long offset = (Long) table.getValueAt(selectedRow, 4);

        onEdt(() -> table.setRowSelectionInterval(selectedRow, selectedRow));

        assertEquals(offset, (long) onEdtGet(() -> preview.getCurrentOffset()));
        assertEquals(offset, (long) onEdtGet(() -> details.getVisualizationModel().getSelectedOffset()));
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
    void authenticodeShowsOneTabPerSignatureAndTheTableAndIssues() throws Exception {
        FullPEData data = load(SIGNED_NESTED);
        select("Authenticode");

        assertEquals("AUTHENTICODE", details.getVisibleCardName());
        AuthenticodePanel panel = details.getAuthenticodePanel();
        List<String> titles = onEdtGet(panel::getTabTitles);
        assertEquals("Signature 1", titles.get(0));
        assertEquals("Signature 2 (nested in 1)", titles.get(1));
        assertEquals("Table", titles.get(2));
        assertTrue(titles.get(3).startsWith("Issues ("), titles.toString());
        assertTrue(visibleText().contains("Integrity: VALID (integrity only, no trust decision)"), visibleText());
        long certificateTable = data.getCertificateTable().get().getOffset();
        assertEquals(certificateTable, (long) onEdtGet(() -> preview.getCurrentOffset()));
    }

    @Test
    void authenticodeKeepsTheTabWhenSwitchingToDecimal() throws Exception {
        FullPEData data = load(SIGNED);
        select("Authenticode");
        AuthenticodePanel panel = details.getAuthenticodePanel();
        onEdt(() -> panel.selectTab(AuthenticodePanel.TABLE_TAB));

        onEdt(() -> {
            details.setHexEnabled(false);
            details.showAuthenticode(); // what the main frame does after the switch
        });

        assertEquals(AuthenticodePanel.TABLE_TAB, onEdtGet(panel::getSelectedTabTitle));
        long offset = data.getCertificateTable().get().getOffset();
        assertTrue(onEdtGet(panel::getSummaryText).contains("offset " + offset + ","), onEdtGet(panel::getSummaryText));
    }

    @Test
    void signatureInTheVisualizationSelectsItsTab() throws Exception {
        FullPEData data = load(SIGNED_NESTED);
        select("Authenticode");
        AuthenticodePanel panel = details.getAuthenticodePanel();
        long nested = data.getCertificateTable().get().getAllSignatures().get(1).getLocation().from();

        onEdt(() -> preview.showContentAtOffset(nested + 1, details.getVisualizationModel()));
        assertEquals("Signature 2 (nested in 1)", onEdtGet(panel::getSelectedTabTitle));

        onEdt(() -> {
            panel.selectTab(AuthenticodePanel.TABLE_TAB);
            preview.showContentAtOffset(nested + 1); // a click into a table of the panel
        });
        assertEquals(AuthenticodePanel.TABLE_TAB, onEdtGet(panel::getSelectedTabTitle));
    }

    /**
     * The digest mismatch is described in a long text, the row grows to show all of it.
     */
    @Test
    void longCertificateIssuesWrap() throws Exception {
        load(SIGNED_MODIFIED);
        select("Authenticode");
        AuthenticodePanel panel = details.getAuthenticodePanel();
        String issuesTab = onEdtGet(() -> panel.getTabTitles().stream()
                .filter(title -> title.startsWith(AuthenticodePanel.ISSUES_TAB)).findFirst().get());

        onEdt(() -> {
            panel.selectTab(issuesTab);
            Swing.layout(details, 800, 600);
        });

        assertTrue(hasRowWithSeveralLines(singleVisibleTable()));
    }

    @Test
    void longAnomaliesWrap() throws Exception {
        load(SIGNED_MODIFIED);
        select("Anomalies");

        onEdt(() -> Swing.layout(details, 800, 600));

        assertTrue(hasRowWithSeveralLines(singleVisibleTable()));
    }

    private static boolean hasRowWithSeveralLines(JTable table) throws Exception {
        return onEdtGet(() -> {
            int line = table.getFontMetrics(table.getFont()).getHeight();
            for (int row = 0; row < table.getRowCount(); row++) {
                if (table.getRowHeight(row) >= 2 * line) return true;
            }
            return false;
        });
    }

    @Test
    void peFormatAndHashesShowTheSignature() throws Exception {
        load(SIGNED);
        select("PE Format");
        assertTrue(visibleText().contains("Authenticode: VALID, signed by Microsoft"), visibleText());

        select("Hashes");
        assertTrue(visibleText().contains("Authentihash (SHA-256): "), visibleText());
    }

    @Test
    void unsignedFileIsNotSigned() throws Exception {
        load(NATIVE_X86);
        select("PE Format");
        assertTrue(visibleText().contains("Authenticode: Not signed"), visibleText());
        assertFalse(visibleText().contains("Authentihash"), visibleText());
        assertNull(onEdtGet(() -> Swing.findPath(tree, "Authenticode")));

        // only in the hashes
        select("Hashes");
        assertTrue(visibleText().contains("Authentihash (SHA-256): "), visibleText());
    }

    @Test
    void certificatesAreShownInTheTree() throws Exception {
        FullPEData data = load(SIGNED);
        StructureMap map = data.getStructureMap();

        assertTreeShows(map.getRegions(RegionType.CERTIFICATE).get(0), "Authenticode");
        assertTreeShows(map.getRegions(RegionType.CERTIFICATE_TABLE).get(0), "Authenticode");
        assertEquals("AUTHENTICODE", details.getVisibleCardName());
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

    @Test
    void treeNavigationSelectsTheStructureInTheVisualization() throws Exception {
        FullPEData data = load(NATIVE_X86);
        select("COFF File Header");
        FileRegion selected = onEdtGet(() -> details.getVisualizationModel().getSelectedRegion());
        assertEquals(RegionType.COFF_FILE_HEADER, selected.getType(), "the header, not its first field");

        select("Section Table");
        selected = onEdtGet(() -> details.getVisualizationModel().getSelectedRegion());
        assertEquals(data.getPeData().getSectionTable().getOffset(), selected.getStart());
    }

    @Test
    void overlayNodeSelectsTheOverlay() throws Exception {
        load(OVERLAY);
        select("Overlay");
        FileRegion selected = onEdtGet(() -> details.getVisualizationModel().getSelectedRegion());
        assertEquals(RegionType.OVERLAY, selected.getType());
    }

    @Test
    void previewHighlightsTheSelectedStructure() throws Exception {
        FullPEData data = load(NATIVE_X86);
        select("COFF File Header");
        long coff = data.getPeData().getCOFFFileHeader().getOffset();
        waitFor(() -> preview.getText().startsWith("Offset: 0x" + Long.toHexString(coff)));

        assertTrue(onEdtGet(() -> preview.highlightCount()) > 0);
    }

    @Test
    void clickIntoTheVisualizationShowsTheOffsetInThePreview() throws Exception {
        load(NATIVE_X86);
        select("Visualization");
        long offset = onEdtGet(() -> {
            DetailCanvas canvas = findVisible(details, DetailCanvas.class).get(0);
            Point p = canvas.pixelOf(0x400, Track.STRUCTURE);
            long clicked = canvas.offsetAt(p);
            canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0,
                    p.x, p.y, 1, false, MouseEvent.BUTTON1));
            return clicked;
        });

        assertTrue(offset >= 0);
        assertEquals(offset, (long) onEdtGet(() -> preview.getCurrentOffset()));
        assertEquals(offset, (long) onEdtGet(() -> details.getVisualizationModel().getSelectedOffset()));
        assertEquals("VISUALIZATION", details.getVisibleCardName(), "a click does not leave the visualization");
    }

    @Test
    void structuresAreShownInTheTree() throws Exception {
        FullPEData data = load(NATIVE_X86);
        StructureMap map = data.getStructureMap();
        long peSignature = data.getPeData().getCOFFFileHeader().getOffset() - 4;

        assertTreeShows(map.getRegions(RegionType.COFF_FILE_HEADER).get(0), "COFF File Header");
        assertEquals("TABLE", details.getVisibleCardName());
        assertTreeShows(map.innermostAt(0x3c).get(), "MS DOS Stub");
        assertTreeShows(map.getRegions(RegionType.PE_SIGNATURE).get(0), "PE Format");
        assertTreeShows(map.getRegions(RegionType.SECTION).get(0), "Section Table");
        assertTreeShows(map.getRegions(RegionType.IMPORT_DLL).get(0), "Imports");
        assertTreeShows(map.regionsAt(peSignature).get(0), "PE Format");
    }

    @Test
    void visualizationCanBeOpenedAtAnOffset() throws Exception {
        load(NATIVE_X86);
        onEdt(() -> {
            componentTree.selectVisualization();
            details.zoomVisualizationTo(0x1000);
        });

        assertEquals("VISUALIZATION", details.getVisibleCardName());
        assertEquals(0x1000, (long) onEdtGet(() -> details.getVisualizationModel().getSelectedOffset()));
    }

    @Test
    void clrTablesAreShownInTheTree() throws Exception {
        FullPEData data = load(DOTNET);
        List<FileRegion> tables = data.getStructureMap().getRegions(RegionType.CLR_TABLE);
        assertFalse(tables.isEmpty());
        FileRegion table = tables.get(0);

        boolean found = onEdtGet(() -> componentTree.selectNodeFor(table));
        assertTrue(found);
        String label = onEdtGet(() -> tree.getSelectionPath().getLastPathComponent().toString());
        assertTrue(label.endsWith(" " + table.getName()), label);
    }

    private void assertTreeShows(FileRegion region, String label) throws Exception {
        boolean found = onEdtGet(() -> componentTree.selectNodeFor(region));
        assertTrue(found, region.toString());
        assertEquals(label, onEdtGet(() -> tree.getSelectionPath().getLastPathComponent().toString()),
                region.toString());
    }

    private static void waitFor(java.util.concurrent.Callable<Boolean> condition) throws Exception {
        long end = System.currentTimeMillis() + 5000;
        while (!onEdtGet(condition)) {
            assertTrue(System.currentTimeMillis() < end, "timed out");
            Thread.sleep(10);
        }
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
            preview = new FileContentPreviewPanel();
            details = new PEDetailsPanel(new JPanel(), null, settings, preview);
            // the visualizer needs a size
            Swing.layout(details, 800, 600);
            componentTree = new PEComponentTree(details);
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
        return onEdtGet(() -> {
            List<String> parts = findVisible(details, JTextArea.class).stream()
                    .map(JTextArea::getText).collect(Collectors.toList());
            findVisible(details, InfoCard.class).forEach(card -> parts.add(card.getPlainText()));
            return String.join("\n", parts);
        });
    }
}
