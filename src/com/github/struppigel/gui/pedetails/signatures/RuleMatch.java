package com.github.struppigel.gui.pedetails.signatures;

import java.util.List;

public interface RuleMatch {
    public Object[] toSummaryRow();
    public List<Object[]> toPatternRows();
}
