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

import io.github.struppigel.gui.utils.HexFont;
import io.github.struppigel.gui.utils.PortexSwingUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Label and value rows, the labels in a column of their own.
 */
class InfoGrid extends JPanel {

    private static final String NL = "\n";
    private static final int LABEL_WIDTH = 9;

    private final List<String[]> rows = new ArrayList<>();
    // the height only grows, so that what is below does not jump when rows come and go
    private int tallest;

    InfoGrid() {
        super(new GridBagLayout());
        setBorder(Spacing.padding());
    }

    /**
     * Replaces the rows.
     *
     * @param newRows label and value of each row, with a third element if the value is shown in the font of the
     *                hex view; an empty label continues the row above, a null label makes the value span both
     *                columns
     */
    void setRows(List<String[]> newRows) {
        if (sameRows(newRows)) {
            return;
        }
        rows.clear();
        rows.addAll(newRows);
        removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridy = 0;
        for (String[] row : rows) {
            JLabel value = new JLabel(row[1]);
            PortexSwingUtils.disableHtml(value);
            value.setToolTipText(row[1]);
            if (row.length > 2) {
                value.setFont(HexFont.font(Font.PLAIN, 13));
            }
            c.insets = new Insets(0, 0, 2, 0);
            if (row[0] == null) {
                c.gridx = 0;
                c.gridwidth = 2;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                add(value, c);
            } else {
                JLabel label = new JLabel(row[0]);
                label.setForeground(UIManager.getColor("Label.disabledForeground"));
                c.gridx = 0;
                c.gridwidth = 1;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                c.insets = new Insets(0, 0, 2, Spacing.PAD);
                add(label, c);
                c.gridx = 1;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.insets = new Insets(0, 0, 2, 0);
                add(value, c);
            }
            c.gridy++;
        }
        // takes up the rest, so that the rows are at the top
        c.gridx = 0;
        c.gridwidth = 2;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        add(Box.createGlue(), c);
        revalidate();
        repaint();
    }

    private boolean sameRows(List<String[]> newRows) {
        if (newRows.size() != rows.size()) {
            return false;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (!java.util.Arrays.equals(rows.get(i), newRows.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the rows as lines of text, label and value aligned, for tests
     */
    String getText() {
        StringBuilder text = new StringBuilder();
        for (String[] row : rows) {
            if (text.length() > 0) {
                text.append(NL);
            }
            if (row[0] == null) {
                text.append(row[1]);
            } else {
                text.append(String.format("%-" + LABEL_WIDTH + "s%s", row[0], row[1]));
            }
        }
        return text.toString();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        tallest = Math.max(tallest, size.height);
        return new Dimension(Math.min(size.width, 400), tallest);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, super.getMinimumSize().height);
    }
}
