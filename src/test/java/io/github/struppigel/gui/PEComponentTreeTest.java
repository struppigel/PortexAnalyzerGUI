package io.github.struppigel.gui;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.testsupport.Swing;
import io.github.struppigel.testsupport.TestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static io.github.struppigel.testsupport.TestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class PEComponentTreeTest {

    static List<String> allPE() {
        return TestFiles.ALL_PE;
    }

    @ParameterizedTest
    @MethodSource("allPE")
    void nodesThatAreAlwaysShown(String sample) throws Exception {
        List<String> labels = treeLabels(sample);

        assertTrue(labels.containsAll(Arrays.asList("PE Format", "MS DOS Stub", "COFF File Header", "Optional Header",
                "Standard Fields", "Windows Fields", "Data Directory", "Section Table", "Anomalies", "Hashes",
                "Visualization", "Signatures")), labels.toString());
    }

    @ParameterizedTest
    @MethodSource("allPE")
    void optionalNodesAreShownIfDataIsPresent(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);
        List<String> labels = treeLabels(sample);

        assertEquals(data.getPeData().maybeGetRichHeader().isPresent(), labels.contains("Rich Header"));
        assertEquals(data.hasResources(), labels.contains("Resources"));
        assertEquals(data.hasImports(), labels.contains("Imports"));
        assertEquals(data.hasDelayLoadImports(), labels.contains("Delay Load Imports"));
        assertEquals(data.hasBoundImportEntries(), labels.contains("Bound Imports"));
        assertEquals(data.hasExports(), labels.contains("Exports"));
        assertEquals(data.hasDebugInfo(), labels.contains("Debug"));
        assertEquals(data.hasCertificateTable(), labels.contains("Authenticode"));
        assertEquals(data.overlayExists(), labels.contains("Overlay"));
        assertEquals(data.isDotNet(), labels.contains(".NET Headers"));
    }

    @Test
    void optionalHeaderFieldsAreChildrenOfOptionalHeader() throws Exception {
        PEComponentTree tree = tree(NATIVE_X86);
        TreePath path = onEdtGet(() -> Swing.findPath(jtree(tree), "Windows Fields"));

        List<String> labels = Stream.of(path.getPath()).skip(1).map(Object::toString).collect(Collectors.toList());
        assertEquals(Arrays.asList("PE Format", "Optional Header", "Windows Fields"), labels);
    }

    @Test
    void nativeFile() throws Exception {
        List<String> labels = treeLabels(NATIVE_X86);

        assertTrue(labels.contains("Imports"));
        assertFalse(labels.contains(".NET Headers"));
        assertFalse(labels.contains("Overlay"));
    }

    @Test
    void dotNetFile() throws Exception {
        FullPEData data = TestPEData.load(DOTNET);
        List<String> labels = treeLabels(DOTNET);

        assertTrue(labels.containsAll(Arrays.asList(".NET Headers", "Metadata Root", "Stream Headers", "#~")));
        assertTrue(labels.containsAll(data.getClrTables().keySet()));
    }

    @Test
    void fileWithOverlay() throws Exception {
        assertTrue(treeLabels(OVERLAY).contains("Overlay"));
    }

    @Test
    void fileWithResources() throws Exception {
        List<String> labels = treeLabels(ICONS_X64);

        assertTrue(labels.containsAll(Arrays.asList("Resources", "Icons", "Version Info", "Manifests")),
                labels.toString());
    }

    @Test
    void fileWithoutDirectories() throws Exception {
        List<String> labels = treeLabels(SMALLEST_PE);

        assertFalse(labels.contains("Imports"));
        assertFalse(labels.contains("Resources"));
        assertFalse(labels.contains("Exports"));
    }

    @Test
    void loadingAnotherFileReplacesNodes() throws Exception {
        FullPEData dotNetWithOverlay = TestPEData.load(OVERLAY);
        FullPEData nativeFile = TestPEData.load(NATIVE_X86);
        PEComponentTree tree = onEdtGet(() -> new PEComponentTree(null));

        List<String> labels = onEdtGet(() -> {
            tree.setFileData(dotNetWithOverlay);
            tree.setFileData(nativeFile);
            return Swing.treeLabels(jtree(tree));
        });

        assertFalse(labels.contains(".NET Headers"));
        assertFalse(labels.contains("Overlay"));
        assertEquals(1, labels.stream().filter(l -> l.equals("PE Format")).count());
    }

    @Test
    void nonPEFileHasHashesVisualizationAndSignatures() throws Exception {
        FileData data = TestPEData.loadAny(TestFiles.file(NOT_PE));
        PEComponentTree tree = onEdtGet(() -> new PEComponentTree(null));

        List<String> labels = onEdtGet(() -> {
            tree.setFileData(data);
            return Swing.treeLabels(jtree(tree));
        });

        assertEquals(Arrays.asList("Hashes", "Visualization", "Signatures"), labels);
    }

    @Test
    void peFileAfterNonPEFileHasAllNodes() throws Exception {
        FileData notPE = TestPEData.loadAny(TestFiles.file(NOT_PE));
        FullPEData dotNet = TestPEData.load(DOTNET);
        PEComponentTree tree = onEdtGet(() -> new PEComponentTree(null));

        List<String> labels = onEdtGet(() -> {
            tree.setFileData(dotNet);
            tree.setFileData(notPE);
            tree.setFileData(dotNet);
            return Swing.treeLabels(jtree(tree));
        });

        assertTrue(labels.containsAll(Arrays.asList("PE Format", ".NET Headers", "Anomalies", "Hashes",
                "Visualization", "Signatures")), labels.toString());
        assertTrue(labels.containsAll(dotNet.getClrTables().keySet()));
    }

    @Test
    void selectedNodeCanBeSelectedAgainAfterLoading() throws Exception {
        PEComponentTree tree = tree(OVERLAY);
        assertEquals(Optional.empty(), onEdtGet(tree::getSelectedLabel));

        assertTrue(onEdtGet(() -> tree.selectNode("Signatures")));
        assertEquals(Optional.of("Signatures"), onEdtGet(tree::getSelectedLabel));

        // the GUI loads the file again after the signatures were reloaded
        FullPEData data = TestPEData.load(OVERLAY);
        onEdt(() -> tree.setFileData(data));
        assertTrue(onEdtGet(() -> tree.selectNode("Signatures")));
        assertEquals(Optional.of("Signatures"), onEdtGet(tree::getSelectedLabel));
        assertFalse(onEdtGet(() -> tree.selectNode("no such node")));
    }

    private static List<String> treeLabels(String sample) throws Exception {
        PEComponentTree tree = tree(sample);
        return onEdtGet(() -> Swing.treeLabels(jtree(tree)));
    }

    /**
     * Without a details panel, as long as no node is selected.
     */
    private static PEComponentTree tree(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);
        return onEdtGet(() -> {
            PEComponentTree tree = new PEComponentTree(null);
            tree.setFileData(data);
            return tree;
        });
    }

    private static JTree jtree(PEComponentTree tree) {
        return Swing.findAll(tree, JTree.class).get(0);
    }
}
