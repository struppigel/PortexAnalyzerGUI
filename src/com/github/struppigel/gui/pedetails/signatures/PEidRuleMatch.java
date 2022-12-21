package com.github.struppigel.gui.pedetails.signatures;

import java.util.ArrayList;
import java.util.List;

public class PEidRuleMatch implements RuleMatch {
    final String ruleName;
    private final String pattern;
    private final String location;
    private final Long offset;

    public PEidRuleMatch(String ruleName, String pattern, String location, Long offset) {
        this.ruleName = ruleName;
        this.pattern = pattern;
        this.location = location;
        this.offset = offset;
    }

    // {"Source", "Match name", "Scan mode"};
    public Object[] toSummaryRow() {
        Object[] row = {"PEiD", ruleName, location};
        return row;
    }
    // {"Rule name", "Pattern", "Content", "Offset"}
    public List<Object[]> toPatternRows() {
        List<Object[]> resultList = new ArrayList<>();
        Object[] row = {ruleName, "", pattern, offset};
        resultList.add(row);
        return resultList;
    }
}
