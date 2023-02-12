/**
 * *****************************************************************************
 * Copyright 2023 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */

package com.github.struppigel.gui.utils;

import com.github.katjahahn.parser.StandardField;

import java.util.ArrayList;
import java.util.List;

public class TableContent extends ArrayList<StandardField> {
    private String title;

    public TableContent(List<StandardField> list, String title) {
        super(list);
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
