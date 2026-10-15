package io.github.struppigel.gui.utils;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.parser.FileFormatException;
import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.parser.sections.idata.ImportDLL;
import io.github.struppigel.parser.sections.idata.NameImport;
import io.github.struppigel.parser.sections.idata.OrdinalImport;
import io.github.struppigel.testsupport.TestFiles;
import io.github.struppigel.tools.Hasher;
import io.github.struppigel.tools.anomalies.Anomaly;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.sigscanner.LocationKind;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
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
            // DLL, Category, Name, Description, RVA, Hint, Offset
            assertEquals(7, row.length);
            assertInstanceOf(String.class, row[0]);
            assertInstanceOf(Long.class, row[4]);
            assertInstanceOf(Long.class, row[6]);
            // name imports have an Integer hint, ordinal imports have no hint and no description
            if (!(row[5] instanceof Integer)) {
                assertEquals("", row[5]);
                assertEquals("", row[3]);
            }
        }
        // the offset is the lookup table entry of the function
        assertEquals(lookupEntryOffsets(data.getPeData().loadImports()), offsetsOf(rows));
    }

    @Test
    void delayLoadImportTableRowsHaveTheOffsetOfTheirFunction() throws Exception {
        FullPEData data = TestPEData.load("corkami/delay_imports.exe");
        List<Object[]> rows = data.getDelayLoadEntries();

        assertFalse(rows.isEmpty());
        for (Object[] row : rows) {
            assertEquals(7, row.length);
            assertInstanceOf(Long.class, row[6]);
        }
        assertEquals(lookupEntryOffsets(data.getPeData().loadDelayLoadImports()), offsetsOf(rows));
    }

    /**
     * @return the file offsets of the lookup table entries in the order of the rows: name imports, then ordinal
     * imports of each DLL
     */
    private static List<Long> lookupEntryOffsets(List<ImportDLL> dlls) {
        List<Long> offsets = new ArrayList<>();
        for (ImportDLL dll : dlls) {
            for (NameImport imp : dll.getNameImports()) {
                offsets.add(imp.getLocations().get(0).from());
            }
            for (OrdinalImport imp : dll.getOrdinalImports()) {
                offsets.add(imp.getLocations().get(0).from());
            }
        }
        return offsets;
    }

    private static List<Long> offsetsOf(List<Object[]> rows) {
        List<Long> offsets = new ArrayList<>();
        for (Object[] row : rows) {
            offsets.add((Long) row[6]);
        }
        return offsets;
    }

    @Test
    void anomaliesTableRowsHaveTheOffsetOfTheirFirstLocation() throws Exception {
        int withOffset = 0;
        for (String sample : TestFiles.ALL_PE) {
            FullPEData data = TestPEData.load(sample);
            List<Anomaly> anomalies = data.getAnomalies();
            List<Object[]> rows = data.getAnomaliesTable();
            for (int i = 0; i < rows.size(); i++) {
                // description, type, subtype, field or structure, offset
                assertEquals(5, rows.get(i).length, sample);
                List<PhysicalLocation> locations = anomalies.get(i).locations();
                if (locations.isEmpty()) {
                    assertEquals("", rows.get(i)[4], sample + " " + anomalies.get(i).description());
                } else {
                    assertEquals(locations.get(0).from(), rows.get(i)[4], sample);
                    withOffset++;
                }
            }
        }
        assertTrue(withOffset > 0, "no anomaly with a location in the samples");
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

    @ParameterizedTest
    @MethodSource("allPE")
    void structureMapAndAnomalies(String sample) throws Exception {
        FullPEData data = TestPEData.load(sample);
        StructureMap map = data.getStructureMap();
        long peSignature = data.getPeData().getCOFFFileHeader().getOffset() - 4;

        assertEquals(TestFiles.file(sample).length(), map.getFileLength());
        // not necessarily the innermost region, the headers of tiny files overlap
        assertTrue(map.regionsAt(peSignature).stream().anyMatch(r -> r.getType() == RegionType.PE_SIGNATURE));
        assertNotNull(data.getAnomalies());
        assertEquals(data.getAnomaliesTable().size(), data.getAnomalies().size());
    }

    @Test
    void structureMapContainsOverlayAndResources() throws Exception {
        assertFalse(TestPEData.load(OVERLAY).getStructureMap().getRegions(RegionType.OVERLAY).isEmpty());
        assertFalse(TestPEData.load(RESOURCES).getStructureMap().getRegions(RegionType.RESOURCE).isEmpty());
    }

    @Test
    void overlaySignatures() throws Exception {
        FullPEData data = TestPEData.load(OVERLAY);
        long overlayOffset = data.getOverlay().getOffset();

        assertFalse(data.getOverlaySignatures().isEmpty());
        for (SignatureMatch match : data.getOverlaySignatures()) {
            assertEquals(LocationKind.OVERLAY, match.getLocationKind());
            assertEquals(overlayOffset, match.getRegionStart());
            assertTrue(match.getOffset() >= overlayOffset);
        }
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
            assertInstanceOf(String.class, row[5]);
        }
        // the sample embeds an executable in its resources
        assertTrue(data.getResourceTableEntries().stream().anyMatch(row -> !((String) row[5]).isEmpty()));
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
    void signedFileIsVerified() throws Exception {
        FullPEData data = TestPEData.load(SIGNED);

        assertTrue(data.hasCertificateTable());
        assertTrue(data.getAuthenticodeVerification().get().isValid());
        String sha256 = data.getAuthenticodeVerification().get().getComputedImageDigests().get("SHA-256");
        assertTrue(data.getHashesReport().contains("Authentihash (SHA-256): " + sha256.toLowerCase()),
                data.getHashesReport());
    }

    @Test
    void unsignedFileHasNoCertificateTable() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);

        assertFalse(data.hasCertificateTable());
        assertFalse(data.getAuthenticodeVerification().isPresent());
    }

    /**
     * Like VirusTotal, the hashes show the authentihash of every file, signed or not.
     */
    @Test
    void unsignedFileHasAuthentihash() throws Exception {
        FullPEData data = TestPEData.load(NATIVE_X86);

        assertTrue(data.getHashesReport().contains("Authentihash (SHA-256): " + authentihash(data, "SHA-256")),
                data.getHashesReport());
    }

    /**
     * Verification only computes the SHA-1 digest of a SHA-1 signature, but the SHA-256 authentihash is shown too.
     */
    @Test
    void sha1SignedFileHasSha256Authentihash() throws Exception {
        FullPEData data = TestPEData.load(SIGNED_SHA1);
        String sha1 = data.getAuthenticodeVerification().get().getComputedImageDigests().get("SHA-1").toLowerCase();
        String report = data.getHashesReport();

        String sha256Line = "Authentihash (SHA-256): " + authentihash(data, "SHA-256");
        String sha1Line = "Authentihash (SHA-1): " + sha1;
        assertTrue(report.contains(sha256Line), report);
        assertTrue(report.contains(sha1Line), report);
        assertTrue(report.indexOf(sha256Line) < report.indexOf(sha1Line), report);
        assertEquals(authentihash(data, "SHA-1"), sha1);
    }

    @Test
    void brokenCertificateTableHasAuthentihash() throws Exception {
        FullPEData data = TestPEData.load(CERT_BROKEN);

        assertTrue(data.getHashesReport().contains("Authentihash (SHA-256): " + authentihash(data, "SHA-256")),
                data.getHashesReport());
    }

    @Test
    void noPeFileIsRejected() {
        File file = TestFiles.file(NOT_PE);

        assertThrows(FileFormatException.class, () -> TestPEData.load(file));
    }

    private static String hex(String algorithm, byte[] bytes) throws Exception {
        return hex(MessageDigest.getInstance(algorithm).digest(bytes));
    }

    private static String authentihash(FullPEData data, String algorithm) throws Exception {
        return hex(new Hasher(data.getPeData()).authentihash(MessageDigest.getInstance(algorithm)));
    }

    private static String hex(byte[] digest) {
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
