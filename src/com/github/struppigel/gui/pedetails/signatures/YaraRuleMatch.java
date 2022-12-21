/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui.pedetails.signatures;

import java.util.List;
import java.util.stream.Collectors;

public class YaraRuleMatch implements RuleMatch {
    final String ruleName;
    final List<PatternMatch> patterns;
    final List<String> tags;

    public YaraRuleMatch(String ruleName, List<PatternMatch> patterns, List<String> tags) {
        this.ruleName = ruleName;
        this.patterns = patterns;
        this.tags = tags;
    }

    // {"Source", "Match name", "Scan mode"};
    public Object[] toSummaryRow() {
        Object[] row = {"Yara", ruleName, "Full file"};
        return row;
    }

    public List<Object[]> toPatternRows() {
        return patterns.stream().map(p -> p.toPatternRow(ruleName)).collect(Collectors.toList());
    }
}