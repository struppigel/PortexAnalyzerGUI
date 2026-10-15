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
import io.github.struppigel.tools.layout.RegionType;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;
import io.github.struppigel.tools.visualizer.render.LegendEntry;
import io.github.struppigel.tools.visualizer.render.LegendRenderer;
import io.github.struppigel.tools.visualizer.render.RenderStyle;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Switches the layers of the visualization on and off and shows their colors. Each layer has a section with a check
 * box, the sections stay when the layer is off.
 */
public class LegendPanel extends JPanel {

    private static final int ENTRY_HEIGHT = 22;
    private static final int ALPHA_OFF = 90;
    private static final Set<RegionType> MARKER_TYPES = EnumSet.of(RegionType.ANOMALY, RegionType.SIGNATURE_MATCH);

    private final VisualizationModel model;
    private final List<Section> sections = new ArrayList<>();

    public LegendPanel(VisualizationModel model) {
        super(new BorderLayout());
        this.model = model;
        sections.add(new Section("Byte plot", model::isByteplotVisible, model::setByteplotVisible,
                this::entryClicked, this::isActive));
        sections.add(new Section("Entropy", model::isEntropyVisible, model::setEntropyVisible,
                this::entryClicked, this::isActive));
        sections.add(new Section("PE structure", model::isStructureVisible, model::setStructureVisible,
                this::entryClicked, this::isActive));
        sections.add(new Section("Markers", model::isMarkersVisible, model::setMarkersVisible,
                this::entryClicked, this::isActive));

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                stack.add(new JSeparator());
            }
            stack.add(sections.get(i));
        }
        // the sections are as wide as the view, at the top
        JPanel view = new JPanel(new BorderLayout());
        view.add(stack, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(view, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        model.addListener(new VisualizationModel.Listener() {
            @Override
            public void dataChanged() {
                update();
            }

            @Override
            public void styleChanged() {
                update();
            }

            @Override
            public void listedRegionsChanged() {
                for (Section section : sections) {
                    section.list.repaint();
                }
            }
        });
        update();
    }

    /**
     * Lists the structures of the entry in the structures view, or the structures at the pointer again if the
     * entry's structures are listed already.
     */
    void entryClicked(LegendEntry entry) {
        if (isActive(entry)) {
            model.clearListedRegions();
        } else {
            model.showRegions(entry.getLabel(), entry.getRegions());
        }
    }

    /**
     * @return true if the structures view lists the structures of the entry
     */
    private boolean isActive(LegendEntry entry) {
        return model.hasListedRegions() && entry.getLabel().equals(model.getListedTitle())
                && entry.getRegions().size() == model.getListedRegions().size();
    }

    void update() {
        FileVisualization vis = model.getVisualization();
        List<List<LegendEntry>> entries = new ArrayList<>();
        if (vis == null) {
            for (int i = 0; i < sections.size(); i++) {
                entries.add(new ArrayList<LegendEntry>());
            }
        } else {
            Set<RegionType> structureTypes = EnumSet.noneOf(RegionType.class);
            structureTypes.addAll(RenderStyle.defaults().getPaintedTypes());
            structureTypes.removeAll(MARKER_TYPES);
            entries.add(withoutTitles(LegendRenderer.bytePlotEntries(vis.getColorScheme())));
            entries.add(withoutTitles(LegendRenderer.entropyEntries(vis.getColorScheme())));
            entries.add(withoutTitles(LegendRenderer.entries(vis.getStructureMap(), vis.getColorScheme(), false,
                    false, structureTypes)));
            entries.add(withoutTitles(LegendRenderer.entries(vis.getStructureMap(), vis.getColorScheme(), false,
                    false, MARKER_TYPES)));
        }
        for (int i = 0; i < sections.size(); i++) {
            sections.get(i).show(entries.get(i));
        }
    }

    private static List<LegendEntry> withoutTitles(List<LegendEntry> entries) {
        List<LegendEntry> result = new ArrayList<>();
        for (LegendEntry entry : entries) {
            if (!entry.isTitle()) {
                result.add(entry);
            }
        }
        return result;
    }

    int entryCount() {
        int count = 0;
        for (Section section : sections) {
            count += section.entries.size();
        }
        return count;
    }

    /**
     * A layer: a check box that switches it, an arrow that folds the entries away and the entries.
     */
    private static final class Section extends JPanel {

        private final DefaultListModel<LegendEntry> entries = new DefaultListModel<>();
        private final JList<LegendEntry> list = new JList<>(entries);
        private final JCheckBox check;
        private final BooleanSupplier layerVisible;
        private final JButton arrow = new JButton();
        private boolean folded = true;

        Section(String title, BooleanSupplier layerVisible, Consumer<Boolean> setLayerVisible,
                Consumer<LegendEntry> entryClicked, Predicate<LegendEntry> isActive) {
            super(new BorderLayout());
            this.layerVisible = layerVisible;
            check = new JCheckBox(title, layerVisible.getAsBoolean());
            check.setFont(check.getFont().deriveFont(Font.BOLD));
            check.setFocusable(false);
            check.setToolTipText("Show or hide " + title.toLowerCase());
            ActionListener toggled = e -> {
                setLayerVisible.accept(check.isSelected());
                applyState();
            };
            check.addActionListener(toggled);

            arrow.setIcon(new ArrowIcon(false));
            arrow.setBorderPainted(false);
            arrow.setContentAreaFilled(false);
            arrow.setFocusable(false);
            arrow.setMargin(new Insets(0, 0, 0, 0));
            arrow.setToolTipText("Fold or unfold the colors");
            arrow.addActionListener(e -> {
                folded = !folded;
                applyState();
            });

            JPanel header = new JPanel(new BorderLayout(Spacing.GAP, 0));
            header.setBorder(Spacing.padding(Spacing.GAP / 2, Spacing.GAP, Spacing.GAP / 2, Spacing.GAP));
            header.add(arrow, BorderLayout.WEST);
            header.add(check, BorderLayout.CENTER);

            list.setCellRenderer(new EntryRenderer(isActive));
            list.setFocusable(false);
            list.setOpaque(false);
            list.setFixedCellHeight(ENTRY_HEIGHT);
            list.setSelectionModel(new NoSelection());
            list.setBorder(Spacing.padding(0, Spacing.PAD * 3, Spacing.GAP / 2, Spacing.PAD));
            MouseAdapter clicks = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    LegendEntry entry = clickableEntryAt(e);
                    if (entry != null && SwingUtilities.isLeftMouseButton(e)) {
                        entryClicked.accept(entry);
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    LegendEntry entry = clickableEntryAt(e);
                    list.setCursor(Cursor.getPredefinedCursor(entry == null ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
                    list.setToolTipText(entry == null ? null : isActive.test(entry)
                            ? "Show the structures at the pointer again"
                            : "Show the " + entry.getRegions().size() + " structures of " + entry.getLabel()
                            + " in the structures view");
                }
            };
            list.addMouseListener(clicks);
            list.addMouseMotionListener(clicks);

            add(header, BorderLayout.NORTH);
            add(list, BorderLayout.CENTER);
            applyState();
        }

        void show(List<LegendEntry> shown) {
            entries.clear();
            for (LegendEntry entry : shown) {
                entries.addElement(entry);
            }
            applyState();
        }

        /**
         * @return the entry under the mouse if it has structures to list and its layer is shown, otherwise null
         */
        private LegendEntry clickableEntryAt(MouseEvent e) {
            int index = list.locationToIndex(e.getPoint());
            if (index < 0 || !list.isEnabled() || !list.getCellBounds(index, index).contains(e.getPoint())) {
                return null;
            }
            LegendEntry entry = entries.get(index);
            return entry.getRegions().isEmpty() ? null : entry;
        }

        private void applyState() {
            check.setSelected(layerVisible.getAsBoolean());
            list.setEnabled(check.isSelected());
            list.setVisible(!folded && !entries.isEmpty());
            arrow.setIcon(new ArrowIcon(folded));
            arrow.setEnabled(!entries.isEmpty());
            revalidate();
            repaint();
        }
    }

    private static final class NoSelection extends DefaultListSelectionModel {
        @Override
        public void setSelectionInterval(int index0, int index1) {
        }

        @Override
        public void addSelectionInterval(int index0, int index1) {
        }
    }

    /**
     * A small triangle, pointing down when the entries are shown and to the right when they are folded.
     */
    private static final class ArrowIcon implements Icon {
        private final boolean folded;

        ArrowIcon(boolean folded) {
            this.folded = folded;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Color color = c.isEnabled() ? UIManager.getColor("Label.foreground")
                    : UIManager.getColor("Label.disabledForeground");
            g.setColor(color == null ? Color.gray : color);
            if (folded) {
                g.fillPolygon(new int[]{x + 3, x + 3, x + 8}, new int[]{y + 2, y + 10, y + 6}, 3);
            } else {
                g.fillPolygon(new int[]{x + 1, x + 10, x + 5}, new int[]{y + 3, y + 3, y + 8}, 3);
            }
        }

        @Override
        public int getIconWidth() {
            return 12;
        }

        @Override
        public int getIconHeight() {
            return 12;
        }
    }

    private static final class EntryRenderer extends DefaultListCellRenderer {

        private final Predicate<LegendEntry> isActive;

        EntryRenderer(Predicate<LegendEntry> isActive) {
            this.isActive = isActive;
            // labels can come from the file, e.g., names of resource types
            PortexSwingUtils.disableHtml(this);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, false, false);
            setOpaque(false);
            LegendEntry entry = (LegendEntry) value;
            setText(entry.getLabel());
            if (list.isEnabled() && isActive.test(entry)) {
                // the structures of this entry are listed in the structures view
                setOpaque(true);
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            }
            Color color = entry.getColor();
            if (!list.isEnabled()) {
                color = new Color(color.getRed(), color.getGreen(), color.getBlue(), ALPHA_OFF);
            }
            setIcon(new InfoPane.SwatchIcon(color, entry.isOutlined()));
            return this;
        }
    }
}
