package io.github.struppigel.gui.pedetails.signatures;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class YaraOutputParserTest {

    @Test
    void ruleWithPatterns() {
        List<YaraRuleMatch> matches = YaraScanner.parseOutput(Arrays.asList(
                "IsPE C:\\samples\\file.exe",
                "0x0:$mz: 4D 5A",
                "0x4e:$text: This program cannot be run in DOS mode"));

        assertEquals(1, matches.size());
        YaraRuleMatch match = matches.get(0);
        assertEquals("IsPE", match.ruleName);
        List<Object[]> rows = match.toPatternRows();
        assertEquals(2, rows.size());
        assertArrayEquals(new Object[]{"IsPE", "$mz", " 4D 5A", 0L, "NaN"}, rows.get(0));
        assertArrayEquals(new Object[]{"IsPE", "$text", " This program cannot be run in DOS mode", 0x4eL, "NaN"},
                rows.get(1));
    }

    @Test
    void patternsBelongToTheirRule() {
        List<YaraRuleMatch> matches = YaraScanner.parseOutput(Arrays.asList(
                "First C:\\file.exe",
                "0x10:$a: 41",
                "Second C:\\file.exe",
                "0x20:$b: 42",
                "0x30:$c: 43"));

        assertEquals(2, matches.size());
        assertEquals("First", matches.get(0).ruleName);
        assertEquals(1, matches.get(0).patterns.size());
        assertEquals("Second", matches.get(1).ruleName);
        assertEquals(2, matches.get(1).patterns.size());
        assertEquals(0x30L, matches.get(1).patterns.get(1).offset);
    }

    @Test
    void ruleWithoutPatterns() {
        List<YaraRuleMatch> matches = YaraScanner.parseOutput(Collections.singletonList("NoStrings C:\\file.exe"));

        assertEquals(1, matches.size());
        assertTrue(matches.get(0).patterns.isEmpty());
    }

    @Test
    void patternContentMayContainColons() {
        List<YaraRuleMatch> matches = YaraScanner.parseOutput(Arrays.asList(
                "Url C:\\file.exe",
                "0x100:$url: http://example.com"));

        PatternMatch pattern = matches.get(0).patterns.get(0);
        assertEquals("$url", pattern.patternName);
        assertEquals(" http://example.com", pattern.patternContent);
    }

    @Test
    void linesWithoutRuleOrPatternAreIgnored() {
        List<YaraRuleMatch> matches = YaraScanner.parseOutput(Arrays.asList("", "garbage", "0x10:$orphan: 41"));

        assertTrue(matches.isEmpty());
    }
}
