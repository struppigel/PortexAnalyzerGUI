package io.github.struppigel.gui.utils;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.parser.FileFormatException;
import io.github.struppigel.testsupport.TestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

import static io.github.struppigel.testsupport.TestFiles.*;
import static org.junit.jupiter.api.Assertions.*;

class PELoadWorkerTest {

    static List<String> allPE() {
        return TestFiles.ALL_PE;
    }

    @ParameterizedTest
    @MethodSource("allPE")
    void hashesReportContainsFileHashes(String sample) throws Exception {
        File file = TestFiles.file(sample);
        byte[] bytes = Files.readAllBytes(file.toPath());
        String report = TestPEData.load(sample).getHashesReport();

        assertTrue(report.startsWith("Full File Hashes"));
        assertTrue(report.contains("MD5: " + hex("MD5", bytes)), report);
        assertTrue(report.contains("SHA256: " + hex("SHA-256", bytes)), report);
    }

    @ParameterizedTest
    @MethodSource("allPE")
    void sectionHashesAndEntropiesForEverySection(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);
        int sections = data.getPeData().getSectionTable().getNumberOfSections();

        List<Object[]> hashRows = data.getSectionHashTableEntries();
        assertEquals(2 * sections, hashRows.size());
        for (Object[] row : hashRows) {
            assertEquals(3, row.length);
            assertTrue(row[1].equals("MD5") || row[1].equals("SHA256"));
        }
        for (int i = 1; i <= sections; i++) {
            double entropy = data.getEntropyForSection(i);
            assertTrue(entropy >= 0 && entropy <= 8, "entropy of section " + i + " is " + entropy);
        }
    }

    @Test
    void importTableRows() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);
        List<Object[]> rows = data.getImportTableEntries();

        assertTrue(data.hasImports());
        assertFalse(rows.isEmpty());
        for (Object[] row : rows) {
            // DLL, Category, Name, Description, RVA, Hint
            assertEquals(6, row.length);
            assertInstanceOf(String.class, row[0]);
            assertInstanceOf(Long.class, row[4]);
            // name imports have an Integer hint, ordinal imports have no hint and no description
            if (!(row[5] instanceof Integer)) {
                assertEquals("", row[5]);
                assertEquals("", row[3]);
            }
        }
    }

    @Test
    void dotNetTables() throws Exception {
        FullPEData data = TestPEData.load(DOTNET);
        Map<String, List<List<Object>>> tables = data.getClrTables();
        Map<String, List<String>> headers = data.getClrTableHeaders();

        assertTrue(data.isDotNet());
        assertTrue(data.hasOptimizedStream());
        assertFalse(data.getDotNetStreamHeaderEntries().isEmpty());
        assertFalse(tables.isEmpty());
        assertEquals(tables.keySet(), headers.keySet());
        for (String name : tables.keySet()) {
            assertTrue(name.matches("0x[0-9a-f]{2} \\w+"), name);
            List<String> tableHeaders = headers.get(name);
            assertEquals("Row", tableHeaders.get(0));
            assertEquals("File offset", tableHeaders.get(tableHeaders.size() - 1));
            for (List<Object> row : tables.get(name)) {
                assertEquals(tableHeaders.size(), row.size(), name);
            }
        }
    }

    @Test
    void nativeFileHasNoDotNetData() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);

        assertFalse(data.isDotNet());
        assertFalse(data.hasOptimizedStream());
        assertTrue(data.getClrTables().isEmpty());
    }

    @Test
    void overlay() throws Exception {
        File file = TestFiles.file(OVERLAY);
        FullPEData data = TestPEData.load(OVERLAY);

        assertTrue(data.overlayExists());
        assertTrue(data.getOverlay().getOffset() < file.length());
        assertFalse(TestPEData.load(DOTNET).overlayExists());
    }

    @Test
    void resourceTableRows() throws Exception {
        FullPEData data = TestPEData.load(RESOURCES);

        assertTrue(data.hasResources());
        assertFalse(data.getResourceTableEntries().isEmpty());
        for (Object[] row : data.getResourceTableEntries()) {
            // Type, Name, Language, Offset, Size, Signatures
            assertEquals(6, row.length);
            assertInstanceOf(Long.class, row[3]);
            assertInstanceOf(Long.class, row[4]);
        }
    }

    @Test
    void iconsVersionInfoAndManifest() throws Exception {
        FullPEData data = TestPEData.load(ICONS_X64);

        assertTrue(data.hasIcons());
        assertTrue(data.hasVersionInfo());
        assertFalse(data.getVersionInfoTable().isEmpty());
        assertTrue(data.hasManifest());
        assertTrue(data.getManifests().get(0).contains("assembly"));
    }

    @Test
    void smallestPeHasNoDirectories() throws Exception {
        FullPEData data = TestPEData.load(SMALLEST_PE);

        assertFalse(data.hasImports());
        assertFalse(data.hasResources());
        assertFalse(data.hasExports());
        assertFalse(data.hasDebugInfo());
    }

    @Test
    void debugInfo() throws Exception {
        FullPEData data = TestPEData.load(DEBUG_INFO);

        assertTrue(data.hasDebugInfo());
        for (TableContent content : data.getDebugTableEntries()) {
            assertFalse(content.getTitle().isEmpty());
            assertTrue(content.getDescription().contains("Type: "), content.getDescription());
        }
    }

    @Test
    void noPeFileIsRejected() {
        File file = TestFiles.file(NOT_PE);

        assertThrows(FileFormatException.class, () -> TestPEData.load(file));
    }

    private static String hex(String algorithm, byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte b : MessageDigest.getInstance(algorithm).digest(bytes)) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
