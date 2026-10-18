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

import javax.swing.*;
import java.awt.*;

/**
 * The view of a vertical scroll pane that is as wide as the visible area, so that long texts are cut off instead of
 * making the content wider than the scroll pane.
 */
final class WidthTrackingPanel extends JPanel implements Scrollable {

    private final int unitIncrement;

    /**
     * @param unitIncrement the pixels that one step of the mouse wheel or the arrow buttons scrolls
     */
    WidthTrackingPanel(LayoutManager layout, int unitIncrement) {
        super(layout);
        this.unitIncrement = unitIncrement;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return unitIncrement;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        int visible = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        return Math.max(unitIncrement, visible - unitIncrement);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        // fills a higher viewport, otherwise the viewport's own background shows below the content
        return getParent() instanceof JViewport && getParent().getHeight() > getPreferredSize().height;
    }
}
