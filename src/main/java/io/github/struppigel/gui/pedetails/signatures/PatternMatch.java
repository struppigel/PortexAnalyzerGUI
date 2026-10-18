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
package io.github.struppigel.gui.pedetails.signatures;

public class PatternMatch {
    // null if the match has no file offset; the table shows an empty cell and the preview ignores it
    final Long offset;
    final String patternContent;
    String patternName;
    String location;

    public PatternMatch(long offset, String patternName, String patternContent) {
        this(Long.valueOf(offset), patternName, patternContent, "");
    }

    public PatternMatch(Long offset, String patternName, String patternContent, String location) {
        this.offset = offset;
        this.patternName = patternName;
        this.patternContent = patternContent;
        this.location = location;
    }

    // {"Rule name" , "Pattern name", "Pattern content", "Offset", "Location"};
    public Object[] toPatternRow(String rulename) {
        Object[] row = {rulename, patternName, patternContent, offset, location};
        return row;
    }

}