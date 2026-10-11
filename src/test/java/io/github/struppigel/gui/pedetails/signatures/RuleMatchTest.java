package io.github.struppigel.gui.pedetails.signatures;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleMatchTest {

    @Test
    void patternRow() {
        PatternMatch pattern = new PatternMatch(0x4d, "$mz", "4D 5A");

        assertArrayEquals(new Object[]{"rule", "$mz", "4D 5A", 0x4dL, "NaN"}, pattern.toPatternRow("rule"));
    }

    @Test
    void peidRuleMatchRows() {
        List<PatternMatch> patterns = Arrays.asList(new PatternMatch(1, "", "E8 ??"), new PatternMatch(2, "", "90"));
        PEidRuleMatch match = new PEidRuleMatch("UPX", "Entry Point", patterns, "PEiD");

        assertArrayEquals(new Object[]{"PEiD", "UPX", "Entry Point"}, match.toSummaryRow());
        List<Object[]> rows = match.toPatternRows();
        assertEquals(2, rows.size());
        assertArrayEquals(new Object[]{"UPX", "", "E8 ??", 1L, "NaN"}, rows.get(0));
        assertArrayEquals(new Object[]{"UPX", "", "90", 2L, "NaN"}, rows.get(1));
    }

    @Test
    void yaraRuleMatchRows() {
        List<PatternMatch> patterns = Collections.singletonList(new PatternMatch(0, "$mz", "4D 5A"));
        YaraRuleMatch match = new YaraRuleMatch("IsPE", patterns, Collections.emptyList());

        assertArrayEquals(new Object[]{"Yara", "IsPE", "Full file"}, match.toSummaryRow());
        assertArrayEquals(new Object[]{"IsPE", "$mz", "4D 5A", 0L, "NaN"}, match.toPatternRows().get(0));
    }

    @Test
    void emptyRuleMatchRows() {
        EmptyRuleMatch match = new EmptyRuleMatch();

        assertArrayEquals(new Object[]{"no matches found", "", ""}, match.toSummaryRow());
        assertEquals(1, match.toPatternRows().size());
        assertArrayEquals(new Object[]{"no matches found", "", "", "", ""}, match.toPatternRows().get(0));
    }
}
