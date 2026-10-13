/**
 * *****************************************************************************
 * Copyright 2026 Karsten Hahn
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
package io.github.struppigel.gui.utils;

/**
 * Notified when the user navigates to a file offset, e.g., by selecting a node of the tree or a row of a table.
 */
public interface OffsetListener {

    /**
     * Called in the event dispatch thread.
     *
     * @param offset the file offset
     * @param source who navigated to the offset, null if unknown; lets listeners ignore their own navigation
     */
    void offsetChanged(long offset, Object source);
}
