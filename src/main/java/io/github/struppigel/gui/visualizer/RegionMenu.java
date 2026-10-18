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

import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.layout.StructureMap;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * The context menu of a structure in the lists of the side panel, and the items that go to the other locations of a
 * structure, which the detail view uses too.
 */
final class RegionMenu {

    private RegionMenu() {}

    /**
     * @param invoker    the component that shows the menu, the parent of the save dialog
     * @param current    an offset in the location of the structure that is shown now, -1 if none is, see
     *                   {@link #addLocationItems}
     * @param zoomAction zooms to the structure, null for no zoom item
     * @param navigator  shows the structure in the tree, null for no tree item
     * @return the menu with the items that are available
     */
    static JPopupMenu create(Component invoker, VisualizationModel model, FileRegion region, long current,
                             Consumer<FileRegion> zoomAction, RegionNavigator navigator) {
        JPopupMenu menu = new JPopupMenu();
        if (zoomAction != null) {
            JMenuItem zoom = new JMenuItem("Zoom to " + region.getName());
            zoom.addActionListener(a -> zoomAction.accept(region));
            menu.add(zoom);
        }
        addLocationItems(menu, model, region, current);
        if (navigator != null) {
            JMenuItem tree = new JMenuItem("Show in tree");
            tree.addActionListener(a -> navigator.showInTree(region, region.getStart()));
            menu.add(tree);
        }
        menu.add(RegionSaver.menuItem(invoker, model, region));
        return menu;
    }

    /**
     * @return the selected offset if the structure is the selected one, otherwise -1
     */
    static long selectedOffsetIn(VisualizationModel model, FileRegion region) {
        return region == model.getSelectedRegion() ? model.getSelectedOffset() : -1;
    }

    /**
     * Adds items that go to the next and the previous location of a structure that has several, e.g., the lookup
     * table entry, the name and the IAT slot of an imported function. The locations are in the order of their
     * offsets, after the last comes the first again.
     *
     * @param current an offset in the location that is shown now, -1 if none is; then the next location is the
     *                first and the previous the last
     */
    static void addLocationItems(JPopupMenu menu, VisualizationModel model, FileRegion region, long current) {
        List<PhysicalLocation> locations = new ArrayList<>(region.getLocations());
        if (locations.size() < 2) {
            return;
        }
        locations.sort(Comparator.comparingLong(PhysicalLocation::from));
        int index = -1;
        for (int i = 0; i < locations.size() && index < 0; i++) {
            PhysicalLocation location = locations.get(i);
            if (current >= location.from() && current < location.from() + location.size()) {
                index = i;
            }
        }
        int next = (index + 1) % locations.size();
        int previous = index <= 0 ? locations.size() - 1 : index - 1;
        menu.add(locationItem("Next location", model, region, locations, next));
        menu.add(locationItem("Previous location", model, region, locations, previous));
    }

    private static JMenuItem locationItem(String text, VisualizationModel model, FileRegion region,
                                          List<PhysicalLocation> locations, int index) {
        PhysicalLocation location = locations.get(index);
        JMenuItem item = new JMenuItem(text + " (" + (index + 1) + " of " + locations.size() + ")");
        item.setToolTipText(StructureMap.hexRange(location.from(), location.from() + location.size()));
        item.addActionListener(a -> model.showLocation(region, location.from()));
        return item;
    }
}
