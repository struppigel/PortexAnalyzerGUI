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
package com.github.struppigel.gui.signatures;

public class YaraPatternMatch {
    final long offset;
    final String patternContent;
    final String patternName;
    String location = "NaN";

    public YaraPatternMatch(long offset, String patternName, String patternContent) {
        this.offset = offset;
        this.patternName = patternName;
        this.patternContent = patternContent;
    }

    // {"Rule name" , "Pattern name", "Pattern content", "Offset", "Location"};
    // TODO location cannot be implemented here because it needs to parse the whole PE again and this is only a getter
    public Object[] toPatternRow(String rulename) {
        Object[] row = {rulename, patternName, patternContent, offset, location};
        return row;
    }

}