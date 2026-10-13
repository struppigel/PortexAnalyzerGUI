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
import io.github.struppigel.tools.visualizer.render.Track;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.LongConsumer;

/**
 * The small overview of the file at the side of the main window. A click shows the structure in the tree.
 */
public class CompactVisualizerPanel extends JPanel {

    private static final int TRACK_WIDTH = 48;

    private final VisualizationModel model;
    private final OverviewStrip overview;
    private RegionNavigator navigator;
    private LongConsumer openVisualization;

    public CompactVisualizerPanel(VisualizationModel model) {
        super(new BorderLayout());
        this.model = model;
        this.overview = new OverviewStrip(model, TRACK_WIDTH, false);
        overview.setTooltipHint("double-click to show it in the visualization");
        add(overview, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
        overview.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                long offset = overview.offsetAt(e.getPoint());
                if (e.getClickCount() == 2) {
                    if (offset >= 0 && openVisualization != null) {
                        Track track = overview.trackAt(e.getPoint());
                        if (track != null) {
                            model.setHexBackground(track); // the hex view continues the clicked track
                        }
                        openVisualization.accept(offset);
                    }
                } else {
                    clicked(offset);
                }
            }
        });
    }

    /**
     * @param action opens the visualization zoomed in to the offset, called on a double click
     */
    public void setOpenVisualizationAction(LongConsumer action) {
        this.openVisualization = action;
    }

    private void clicked(long offset) {
        if (offset < 0) {
            return;
        }
        FileRegion region = model.innermostAt(offset);
        model.selectRegion(region, offset);
        if (region != null && navigator != null) {
            navigator.showInTree(region, offset);
        }
    }

    /**
     * @param navigator shows the clicked structure in the rest of the GUI
     */
    public void setRegionNavigator(RegionNavigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public Dimension getPreferredSize() {
        // fixed width, so that the panel does not change its size with the content
        Dimension overviewSize = overview.getPreferredSize();
        Insets insets = getInsets();
        return new Dimension(overviewSize.width + insets.left + insets.right, overviewSize.height);
    }

    OverviewStrip getOverview() {
        return overview;
    }
}
