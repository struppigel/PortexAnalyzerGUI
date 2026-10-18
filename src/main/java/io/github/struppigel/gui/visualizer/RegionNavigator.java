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
package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.layout.FileRegion;

/**
 * Shows a structure that the user picked in a visualizer view in the rest of the GUI, i.e., the tree node and the
 * details of the structure.
 */
public interface RegionNavigator {

    /**
     * @param region the picked structure, not null
     * @param offset the offset the user clicked at, which the content preview shall show
     */
    void showInTree(FileRegion region, long offset);
}
