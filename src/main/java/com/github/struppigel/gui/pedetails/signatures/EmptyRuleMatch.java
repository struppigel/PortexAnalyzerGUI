package com.github.struppigel.gui.pedetails.signatures;

import java.util.ArrayList;
import java.util.List;

public class EmptyRuleMatch implements RuleMatch {

    private static final String message = "no matches found";
    @Override
    public Object[] toSummaryRow() {
        Object[] row = {message,"",""};
        return row;
    }

    // {"Rule name" , "Pattern name", "Pattern content", "Offset", "Location"};
    @Override
    public List<Object[]> toPatternRows() {
        List<Object[]> row = new ArrayList<>();
        Object[] obj = {message, "","","",""};
        row.add(obj);
        return row;
    }
}
