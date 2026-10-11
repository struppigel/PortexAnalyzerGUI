package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.gui.utils.TestPEData;
import io.github.struppigel.testsupport.TestFiles;
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
            assertTrue(Arrays.asList("PEiD", "Filetype").contains(row[0]), String.valueOf(row[0]));
            assertTrue(Arrays.asList("Entry Point", "Overlay", "Resource").contains(row[2]), String.valueOf(row[2]));
            assertFalse(match.toPatternRows().isEmpty());
        }
    }

    @Test
    void overlaySignaturesAreFound() throws Exception {
        List<PEidRuleMatch> matches = new PEidScanner(null, TestPEData.load(OVERLAY)).doInBackground();

        List<Object[]> overlayRows = matches.stream()
                .map(PEidRuleMatch::toSummaryRow)
                .filter(row -> row[2].equals("Overlay"))
                .collect(Collectors.toList());
        assertFalse(overlayRows.isEmpty());
        for (Object[] row : overlayRows) {
            assertEquals("Filetype", row[0]);
        }
    }
}
