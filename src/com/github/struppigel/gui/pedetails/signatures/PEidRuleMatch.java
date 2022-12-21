package com.github.struppigel.gui.pedetails.signatures;

import java.util.List;
import java.util.stream.Collectors;

public class PEidRuleMatch implements RuleMatch {
    final String ruleName;
    private final List<PatternMatch> patterns;
    private final String scanMode;


    public PEidRuleMatch(String ruleName, String scanMode, List<PatternMatch> patterns) {
        this.ruleName = ruleName;
        this.patterns = patterns;
        this.scanMode = scanMode;
    }

    // {"Source", "Match name", "Scan mode"};
    public Object[] toSummaryRow() {
        Object[] row = {"PEiD", ruleName, scanMode};
        return row;
    }
    // {"Rule name", "Pattern", "Content", "Offset"}
    public List<Object[]> toPatternRows() {
        return patterns.stream().map(p -> p.toPatternRow(ruleName)).collect(Collectors.toList());
    }
}
