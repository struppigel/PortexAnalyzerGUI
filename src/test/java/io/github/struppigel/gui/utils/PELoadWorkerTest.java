package io.github.struppigel.gui.utils;

import io.github.struppigel.gui.FileData;
import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.usersignatures.UserSignatureSet;
import io.github.struppigel.gui.usersignatures.UserSignatures;
import io.github.struppigel.parser.AnalysisInterruptedException;
import io.github.struppigel.parser.PEData;
import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.parser.sections.idata.ImportDLL;
import io.github.struppigel.parser.sections.idata.NameImport;
import io.github.struppigel.parser.sections.idata.OrdinalImport;
import io.github.struppigel.testsupport.TestFiles;
import io.github.struppigel.tools.Hasher;
import io.github.struppigel.tools.anomalies.Anomaly;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.layout.StructureMap;
import io.github.struppigel.tools.sigscanner.LocationKind;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    void noPeFileOpensWithoutPEStructures() throws Exception {
        File file = TestFiles.file(NOT_PE);
        FileData data = TestPEData.loadAny(file);

        assertFalse(data.isPE());
        assertFalse(data.getPeParseError().isPresent());
        assertEquals(file, data.getFile());
        assertEquals(file.length(), data.getStructureMap().getFileLength());
        for (FileRegion region : data.getStructureMap().getRegions()) {
            assertEquals(RegionType.SIGNATURE_MATCH, region.getType(), region.toString());
        }
        assertThrows(AssertionError.class, () -> TestPEData.load(file));
    }

    @Test
    void textFileHasMd5Sha1Sha256(@TempDir Path dir) throws Exception {
        File file = dir.resolve("text.txt").toFile();
        byte[] bytes = "no PE file, just some text".getBytes(StandardCharsets.US_ASCII);
        Files.write(file.toPath(), bytes);
        String report = TestPEData.loadAny(file).getHashesReport();

        assertTrue(report.startsWith("Full File Hashes"));
        assertTrue(report.contains("MD5: " + hex("MD5", bytes)), report);
        assertTrue(report.contains("SHA1: " + hex("SHA-1", bytes)), report);
        assertTrue(report.contains("SHA256: " + hex("SHA-256", bytes)), report);
    }

    @Test
    void emptyFileOpens(@TempDir Path dir) throws Exception {
        File file = dir.resolve("empty.bin").toFile();
        Files.write(file.toPath(), new byte[0]);
        FileData data = TestPEData.loadAny(file);

        assertFalse(data.isPE());
        assertTrue(data.getSignatureMatches().isEmpty());
        assertEquals(0, data.getStructureMap().getFileLength());
        assertTrue(data.getHashesReport().contains("MD5: d41d8cd98f00b204e9800998ecf8427e"), data.getHashesReport());
    }

    @Test
    void zipMagicIsASignatureAndAMarker(@TempDir Path dir) throws Exception {
        File file = dir.resolve("archive.zip").toFile();
        byte[] bytes = new byte[0x40];
        bytes[0] = 0x50;
        bytes[1] = 0x4B;
        bytes[2] = 0x03;
        bytes[3] = 0x04;
        Files.write(file.toPath(), bytes);
        FileData data = TestPEData.loadAny(file);

        assertTrue(data.getSignatureMatches().stream().anyMatch(m -> m.getName().equals("PKZIP archive_1")),
                data.getSignatureMatches().toString());
        FileRegion marker = data.getStructureMap().innermostAt(0).get();
        assertEquals(RegionType.SIGNATURE_MATCH, marker.getType());
        assertEquals("PKZIP archive_1", marker.getName());
    }

    @Test
    void malformedPEFallsBackWithTheError() throws Exception {
        File file = TestFiles.file(SMALLEST_PE);
        PELoadWorker worker = new PELoadWorker(file, null, new JLabel()) {
            @Override
            FullPEData loadPE(PEData data) {
                throw new IllegalStateException("broken header");
            }
        };
        FileData data = worker.doInBackground();

        assertFalse(data.isPE());
        assertEquals(Optional.of("broken header"), data.getPeParseError());
        assertTrue(data.getHashesReport().contains("SHA1: "), data.getHashesReport());
        assertEquals(file.length(), data.getStructureMap().getFileLength());
    }

    @Test
    void interruptionIsNotAFallback() {
        PELoadWorker worker = new PELoadWorker(TestFiles.file(SMALLEST_PE), null, new JLabel()) {
            @Override
            FullPEData loadPE(PEData data) {
                throw new AnalysisInterruptedException("stopped");
            }
        };

        assertThrows(AnalysisInterruptedException.class, worker::doInBackground);
    }

    @Test
    void directoryIsRejected(@TempDir Path dir) {
        assertThrows(IOException.class, () -> TestPEData.loadAny(dir.toFile()));
    }

    private static final String MINE_SIG = "[My overlay MZ]\npattern = 'MZ'\nat = overlay\n\n"
            + "[My resource MZ]\npattern = 'MZ'\nat = resource\n\n"
            + "[My ZIP]\npattern = 'PK' 03 04\nat = file\n";

    private static Path signatureFolder(Path dir, String fileName, String text) throws IOException {
        Path folder = dir.resolve("signatures");
        Files.createDirectories(folder);
        Files.write(folder.resolve(fileName), text.getBytes(StandardCharsets.UTF_8));
        return folder;
    }

    private static FileData loadWithUserSignatures(File file, Path signatureFolder) throws Exception {
        return new PELoadWorker(file, null, new JLabel(), new UserSignatures(signatureFolder.toFile()))
                .doInBackground();
    }

    private static File zipFile(Path dir) throws IOException {
        File file = dir.resolve("archive.zip").toFile();
        byte[] bytes = new byte[0x40];
        bytes[0] = 0x50;
        bytes[1] = 0x4B;
        bytes[2] = 0x03;
        bytes[3] = 0x04;
        Files.write(file.toPath(), bytes);
        return file;
    }

    private static List<String> names(List<SignatureMatch> matches) {
        List<String> names = new ArrayList<>();
        matches.forEach(m -> names.add(m.getName()));
        return names;
    }

    @Test
    void invalidChecksumIsAnAnomaly(@TempDir Path dir) throws Exception {
        Path copy = dir.resolve("strings.exe");
        byte[] bytes = Files.readAllBytes(TestFiles.file(SIGNED).toPath());
        // the file was valid, changing its last byte invalidates the checksum
        bytes[bytes.length - 1] ^= 0x11;
        Files.write(copy, bytes);
        FullPEData data = TestPEData.load(copy.toFile());

        assertTrue(data.getChecksumCheck().get().isInvalid());
        assertTrue(data.getAnomaliesTable().stream().anyMatch(row ->
                        String.valueOf(row[0]).startsWith("Optional Header: checksum is invalid (0x55d4a)")),
                () -> data.getAnomaliesTable().stream().map(row -> String.valueOf(row[0]))
                        .collect(java.util.stream.Collectors.toList()).toString());
        assertTrue(TestPEData.load(SIGNED).getChecksumCheck().get().isValid());
    }

    @Test
    void withoutUserSignaturesThereAreNoUserMatches() throws Exception {
        FileData data = TestPEData.load(OVERLAY);
        assertNull(data.getUserSignatures().getSignatures().getFolder());
        assertTrue(data.getUserSignatureMatches().isEmpty());
    }

    @Test
    void userSignaturesInTheOverlay(@TempDir Path dir) throws Exception {
        FullPEData data = (FullPEData) loadWithUserSignatures(TestFiles.file(OVERLAY),
                signatureFolder(dir, "mine.sig", MINE_SIG));
        long overlayOffset = data.getOverlay().getOffset();

        List<SignatureMatch> overlay = data.getUserSignatures().getMatches(LocationKind.OVERLAY);
        assertEquals(List.of("My overlay MZ"), names(overlay));
        assertEquals(overlayOffset, overlay.get(0).getOffset());
        assertEquals("mine.sig", UserSignatureSet.sourceOf(overlay.get(0)));
        // the matches of the bundled signatures stay apart, the markers show both
        assertTrue(data.getSignatureMatches().stream().noneMatch(m -> m.getName().startsWith("My ")));
        assertTrue(data.getOverlaySignatures().stream().noneMatch(m -> m.getName().startsWith("My ")));
        assertTrue(data.getStructureMap().getRegions(RegionType.SIGNATURE_MATCH).stream()
                .anyMatch(r -> r.getName().equals("My overlay MZ") && r.getStart() == overlayOffset));
    }

    @Test
    void userSignaturesInTheResourceTable(@TempDir Path dir) throws Exception {
        FullPEData data = (FullPEData) loadWithUserSignatures(TestFiles.file(RESOURCES),
                signatureFolder(dir, "mine.sig", MINE_SIG));

        assertTrue(data.getResourceTableEntries().stream()
                        .anyMatch(row -> ((String) row[5]).contains("My resource MZ from mine.sig")),
                () -> data.getResourceTableEntries().stream().map(row -> (String) row[5])
                        .collect(java.util.stream.Collectors.toList()).toString());
    }

    @Test
    void userSignaturesInFilesThatAreNoPEFiles(@TempDir Path dir) throws Exception {
        FileData data = loadWithUserSignatures(zipFile(dir), signatureFolder(dir, "mine.sig", MINE_SIG));

        assertFalse(data.isPE());
        assertEquals(List.of("My ZIP"), names(data.getUserSignatureMatches()));
        assertTrue(data.getStructureMap().getRegions(RegionType.SIGNATURE_MATCH).stream()
                .anyMatch(r -> r.getName().equals("My ZIP")));
    }

    @Test
    void malformedUserSignaturesAreSkippedWithWarnings(@TempDir Path dir) throws Exception {
        Path folder = signatureFolder(dir, "broken.sig",
                "[Broken]\npattern = 0G\nat = file\n\n[My ZIP]\npattern = 'PK' 03 04\nat = file\n");
        FileData data = loadWithUserSignatures(zipFile(dir), folder);

        assertEquals(List.of("My ZIP"), names(data.getUserSignatureMatches()));
        UserSignatureSet signatures = data.getUserSignatures().getSignatures();
        assertTrue(signatures.hasSkippedSignatures());
        assertTrue(signatures.getLoadWarnings().get(0).startsWith("broken.sig:2:"),
                signatures.getLoadWarnings().toString());
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
