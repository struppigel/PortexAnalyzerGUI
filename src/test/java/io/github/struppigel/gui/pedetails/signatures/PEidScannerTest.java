package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.gui.usersignatures.UserSignatureSet;
import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.testsupport.TestFiles;
import io.github.struppigel.tools.sigscanner.LocationKind;
import io.github.struppigel.tools.sigscanner.SignatureDatabase;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import io.github.struppigel.tools.sigscanner.SignatureScanner;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.github.struppigel.testsupport.TestFiles.OVERLAY;
import static org.junit.jupiter.api.Assertions.*;

class PEidScannerTest {

    static List<String> allPE() {
        return TestFiles.ALL_PE;
    }

    @ParameterizedTest
    @MethodSource("allPE")
    void summaryRowsHaveKnownSourcesAndScanModes(String sample) throws Exception {
        List<PEidRuleMatch> matches = new PEidScanner(null, TestPEData.load(sample)).doInBackground();

        for (PEidRuleMatch match : matches) {
            Object[] row = match.toSummaryRow();
            assertEquals(3, row.length);
            assertEquals("PortEx", row[0]);
            assertTrue(Arrays.asList("Entry Point", "Section", "Overlay", "Resource", "MS-DOS Header").contains(row[2]),
                    String.valueOf(row[2]));
            assertFalse(match.toPatternRows().isEmpty());
            for (Object[] pattern : match.toPatternRows()) {
                // Match name, Pattern name, Pattern content, Offset, Location
                assertEquals(5, pattern.length);
                assertEquals(row[1], pattern[0]);
                assertTrue(pattern[3] == null || pattern[3] instanceof Long);
                assertFalse(String.valueOf(pattern[4]).isEmpty());
            }
        }
    }

    @Test
    void overlaySignaturesAreFound() throws Exception {
        List<PEidRuleMatch> matches = new PEidScanner(null, TestPEData.load(OVERLAY)).doInBackground();

        List<PEidRuleMatch> overlayMatches = matches.stream()
                .filter(m -> m.toSummaryRow()[2].equals("Overlay"))
                .collect(Collectors.toList());
        assertFalse(overlayMatches.isEmpty());
        for (PEidRuleMatch match : overlayMatches) {
            String name = String.valueOf(match.toSummaryRow()[1]);
            assertFalse(name.startsWith("["), "names have no brackets anymore: " + name);
            for (Object[] pattern : match.toPatternRows()) {
                assertTrue(String.valueOf(pattern[4]).startsWith("overlay+0x"), String.valueOf(pattern[4]));
            }
        }
    }

    @Test
    void scanModesForLocations() {
        assertEquals("Entry Point", PEidScanner.scanModeOf(LocationKind.ENTRY_POINT));
        assertEquals("MS-DOS Header", PEidScanner.scanModeOf(LocationKind.MSDOS_HEADER));
        assertEquals("Debug data", PEidScanner.scanModeOf(LocationKind.DEBUG_DATA));
        assertEquals("File", PEidScanner.scanModeOf(LocationKind.FILE));
        assertEquals("End of file", PEidScanner.scanModeOf(LocationKind.EOF));
    }

    @Test
    void nonPEMatchesHaveScanModeFile(@TempDir Path dir) throws Exception {
        File file = dir.resolve("archive.zip").toFile();
        byte[] bytes = new byte[0x40];
        bytes[0] = 0x50;
        bytes[1] = 0x4B;
        bytes[2] = 0x03;
        bytes[3] = 0x04;
        Files.write(file.toPath(), bytes);

        List<PEidRuleMatch> matches = new PEidScanner(null, TestPEData.loadAny(file)).doInBackground();

        assertTrue(matches.stream().anyMatch(m -> m.toSummaryRow()[1].equals("PKZIP archive_1")
                && m.toSummaryRow()[2].equals("File")), matches.toString());
    }

    @Test
    void userSignaturesHaveTheirDatabaseAsSource() {
        String text = "[Same name]\npattern = 01 02\nat = file\n";
        SignatureDatabase db = SignatureDatabase.merge(SignatureDatabase.parse(text, "a.sig"),
                SignatureDatabase.parse(text, "b.sig"));
        List<SignatureMatch> scanned = new SignatureScanner(db).scanBytes(new byte[]{1, 2, 3});

        List<PEidRuleMatch> matches = PEidScanner.toRuleMatches(scanned, UserSignatureSet::sourceOf);

        // one row per database, although the names are the same
        assertEquals(2, matches.size(), matches.toString());
        assertArrayEquals(new Object[]{"a.sig", "Same name", "File"}, matches.get(0).toSummaryRow());
        assertArrayEquals(new Object[]{"b.sig", "Same name", "File"}, matches.get(1).toSummaryRow());
        assertEquals("PortEx", PEidScanner.toRuleMatches(scanned).get(0).toSummaryRow()[0]);
    }
}
