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

import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.render.LegendEntry;
import io.github.struppigel.tools.visualizer.render.LegendRenderer;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The colors of the structures of the file and of the byte plot and entropy.
 */
public class LegendPanel extends JPanel {

    private final VisualizationModel model;
    private final DefaultListModel<LegendEntry> entries = new DefaultListModel<>();

    public LegendPanel(VisualizationModel model) {
        super(new BorderLayout());
        this.model = model;
        JList<LegendEntry> list = new JList<>(entries);
        list.setCellRenderer(new EntryRenderer());
        list.setFocusable(false);
        add(new JScrollPane(list), BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 220));
        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void dataChanged() {
                update();
            }

            @Override
            public void styleChanged() {
                update();
            }
        });
        update();
    }

    void update() {
        entries.clear();
        FileVisualization vis = model.getVisualization();
        if (vis == null) {
            return;
        }
        List<LegendEntry> legend = LegendRenderer.entries(vis.getStructureMap(), vis.getColorScheme(),
                model.isByteplotVisible(), model.isEntropyVisible(), model.getStyle().getPaintedTypes());
        for (LegendEntry entry : legend) {
            entries.addElement(entry);
        }
    }

    int entryCount() {
        return entries.size();
    }

    private static final class EntryRenderer extends DefaultListCellRenderer {

        EntryRenderer() {
            // labels can come from the file, e.g., names of resource types
            PortexSwingUtils.disableHtml(this);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, false, false);
            LegendEntry entry = (LegendEntry) value;
            setText(entry.getLabel());
            if (entry.isTitle()) {
                setFont(getFont().deriveFont(Font.BOLD));
                setIcon(null);
            } else {
                setIcon(new InfoPane.SwatchIcon(entry.getColor(), entry.isOutlined()));
            }
            return this;
        }
    }
}
