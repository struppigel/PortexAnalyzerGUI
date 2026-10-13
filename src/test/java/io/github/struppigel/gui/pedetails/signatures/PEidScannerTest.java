package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.testsupport.TestFiles;
import io.github.struppigel.tools.sigscanner.LocationKind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;

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
    }
}
