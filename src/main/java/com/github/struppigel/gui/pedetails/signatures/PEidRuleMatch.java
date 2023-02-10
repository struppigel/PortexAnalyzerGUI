package com.github.struppigel.gui.pedetails.signatures;

import java.util.List;
import java.util.stream.Collectors;

public class PEidRuleMatch implements RuleMatch {
    final String ruleName;
    private final List<PatternMatch> patterns;
    private final String scanMode;
    private final String scannerName;


    public PEidRuleMatch(String ruleName, String scanMode, List<PatternMatch> patterns, String scannerName) {
        this.ruleName = ruleName;
        this.patterns = patterns;
        this.scanMode = scanMode;
        this.scannerName = scannerName;
    }

    // {"Source", "Match name", "Scan mode"};
    public Object[] toSummaryRow() {
        Object[] row = {scannerName, ruleName, scanMode};
        return row;
    }
    // {"Rule name", "Pattern", "Content", "Offset"}
    public List<Object[]> toPatternRows() {
        return patterns.stream().map(p -> p.toPatternRow(ruleName)).collect(Collectors.toList());
    }
}
