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
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Label and value rows, the labels in a column of their own. A click on a row marks its value in the color of the
 * selection, like a click on a row of a table.
 */
class InfoGrid extends JPanel {

    /** marks a value that is shown in the font of the hex view */
    static final String MONO = "mono";
    /** marks a heading */
    static final String TITLE = "title";
    private static final String NL = "\n";
    private static final int LABEL_WIDTH = 9;
    /** the characters of a copied value that the menu shows */
    private static final int MAX_PREVIEW = 24;
    /** the room above and below the text of a row, the rows of a value that spans several are marked as one block */
    private static final int ROW_PAD = 1;
    /** the room left and right of the text, so that the mark does not touch it */
    private static final int MARK_PAD = 3;

    private final List<String[]> rows = new ArrayList<>();
    // the value of each row
    private final List<ValueLabel> values = new ArrayList<>();
    // the first and last row of the marked value, -1 if none is marked
    private int markedFrom = -1;
    private int markedTo = -1;
    private Function<String, JMenuItem> copyAllItem;
    // the height only grows, so that what is below does not jump when rows come and go
    private int tallest;

    InfoGrid() {
        super(new GridBagLayout());
        setBorder(Spacing.padding());
        // beside the rows, all rows can be copied
        addMouseListener(new RowMouseHandler(-1));
    }

    /**
     * Replaces "Copy all", which copies all rows, in the menu of the rows.
     *
     * @param copyAllItem makes the item from the label of the value that the menu is for, null if the menu is for no
     *                    value; returns null to offer none
     */
    void setCopyAllItem(Function<String, JMenuItem> copyAllItem) {
        this.copyAllItem = copyAllItem;
    }

    /**
     * @param row the row that the menu is for, null for none
     * @return a menu that copies the value of the row, if it has one, and all rows or what
     *         {@link #setCopyAllItem(Function)} offers instead
     */
    JPopupMenu createMenu(String[] row) {
        JPopupMenu menu = new JPopupMenu();
        String copied = row == null ? null : copiedText(row);
        if (copied != null && !copied.isEmpty()) {
            String preview = copied.length() > MAX_PREVIEW ? copied.substring(0, MAX_PREVIEW) + "..." : copied;
            JMenuItem item = new JMenuItem("Copy " + preview);
            item.addActionListener(a -> copyToClipboard(copied));
            menu.add(item);
        }
        if (copyAllItem == null) {
            JMenuItem all = new JMenuItem("Copy all");
            all.addActionListener(a -> copyToClipboard(getText()));
            menu.add(all);
        } else {
            JMenuItem all = copyAllItem.apply(row == null ? null : valueLabel(row));
            if (all != null) {
                menu.add(all);
            }
        }
        return menu;
    }

    /**
     * @return what copying the row copies: the value, or the whole value of which the row shows a part; null for
     *         headings and hints, which have no label
     */
    static String copiedText(String[] row) {
        if (row[0] == null) {
            return null;
        }
        return row.length > 3 && row[3] != null ? row[3] : row[1];
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    /**
     * @return the label of the first row of the value that the row shows, the label of the row if it is not shown
     */
    private String valueLabel(String[] row) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i) == row) {
                return rows.get(valueRows(i)[0])[0];
            }
        }
        return row[0];
    }

    /**
     * @return the first and the last row of the value that the row shows a part of
     */
    int[] valueRows(int index) {
        int from = index;
        while (from > 0 && continuesAbove(from)) {
            from--;
        }
        int to = index;
        while (to + 1 < rows.size() && continuesAbove(to + 1)) {
            to++;
        }
        return new int[]{from, to};
    }

    /**
     * @return true if the row shows another part of the value of the row above, i.e., it has no label of its own and
     *         copies the same whole value
     */
    private boolean continuesAbove(int index) {
        String[] row = rows.get(index);
        String[] above = rows.get(index - 1);
        return "".equals(row[0]) && row.length > 3 && row[3] != null && above.length > 3
                && row[3].equals(above[3]);
    }

    /**
     * Marks the value that the row shows, over all of its rows, unless the row shows no value.
     */
    void markValue(int index) {
        String copied = copiedText(rows.get(index));
        if (copied == null || copied.isEmpty()) {
            return;
        }
        int[] value = valueRows(index);
        markedFrom = value[0];
        markedTo = value[1];
        showMark();
    }

    private void showMark() {
        for (int i = 0; i < values.size(); i++) {
            values.get(i).setMarked(i >= markedFrom && i <= markedTo);
        }
    }

    /**
     * @return true if the value of the row is marked, for tests
     */
    boolean isMarked(int index) {
        return values.get(index).isMarked();
    }

    /**
     * @return the component that shows the value of the row, for tests
     */
    JLabel getValueLabel(int index) {
        return values.get(index);
    }

    /**
     * Lets the height shrink to the next rows, e.g., when the selection changes, which the user caused.
     */
    void resetHeight() {
        tallest = 0;
        revalidate();
    }

    /**
     * Replaces the rows. A click on a row marks its value, a right click offers to copy it and all rows. The marked
     * value stays marked if its rows stay.
     *
     * @param newRows label and value of each row, with a third element {@link #MONO} if the value is shown in the
     *                font of the hex view or {@link #TITLE} for a heading, and an optional fourth element that
     *                copying the row copies instead of the value, e.g., the whole text of which the row shows a part;
     *                an empty label continues the row above, a null label makes the value span both columns
     */
    void setRows(List<String[]> newRows) {
        if (sameRows(newRows)) {
            return;
        }
        if (markedFrom >= 0 && !sameRows(newRows, markedFrom, markedTo)) {
            markedFrom = -1;
            markedTo = -1;
        }
        rows.clear();
        rows.addAll(newRows);
        values.clear();
        removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.gridy = 0;
        for (int index = 0; index < rows.size(); index++) {
            String[] row = rows.get(index);
            ValueLabel value = new ValueLabel(row[1]);
            values.add(value);
            String style = row.length > 2 ? row[2] : "";
            if (MONO.equals(style)) {
                value.setFont(HexFont.font(Font.PLAIN, 13));
            } else if (TITLE.equals(style)) {
                value.setFont(value.getFont().deriveFont(Font.BOLD));
            }
            // a heading separates what is below from the rows above
            int top = TITLE.equals(style) && c.gridy > 0 ? Spacing.GAP : 0;
            c.insets = new Insets(top, 0, 0, 0);
            RowMouseHandler handler = new RowMouseHandler(index);
            value.addMouseListener(handler);
            if (row[0] == null) {
                c.gridx = 0;
                c.gridwidth = 2;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                add(value, c);
            } else {
                JLabel label = new JLabel(row[0]);
                label.setForeground(UIManager.getColor("Label.disabledForeground"));
                label.setBorder(BorderFactory.createEmptyBorder(ROW_PAD, MARK_PAD, ROW_PAD, 0));
                label.addMouseListener(handler);
                c.gridx = 0;
                c.gridwidth = 1;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                c.insets = new Insets(top, 0, 0, Spacing.PAD - MARK_PAD);
                add(label, c);
                c.gridx = 1;
                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                c.insets = new Insets(top, 0, 0, 0);
                add(value, c);
            }
            c.gridy++;
        }
        showMark();
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
        return newRows.size() == rows.size() && sameRows(newRows, 0, rows.size() - 1);
    }

    /**
     * @return true if the new rows from the first to the last are the shown ones
     */
    private boolean sameRows(List<String[]> newRows, int first, int last) {
        if (last >= newRows.size() || last >= rows.size()) {
            return false;
        }
        for (int i = first; i <= last; i++) {
            if (!Arrays.equals(rows.get(i), newRows.get(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return the shown rows, for tests
     */
    List<String[]> getRows() {
        return Collections.unmodifiableList(rows);
    }

    /**
     * @return the rows as lines of text, label and value aligned, for tests and to copy all rows
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

    /**
     * Marks the value of a row on a click and opens the menu of the row on a right click.
     */
    private final class RowMouseHandler extends MouseAdapter {
        // -1 for the room beside the rows
        private final int index;

        RowMouseHandler(int index) {
            this.index = index;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            open(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            open(e);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (index >= 0 && SwingUtilities.isLeftMouseButton(e)) {
                markValue(index);
            }
        }

        private void open(MouseEvent e) {
            if (!e.isPopupTrigger()) {
                return;
            }
            JPopupMenu menu = createMenu(index < 0 ? null : rows.get(index));
            if (menu.getComponentCount() > 0) {
                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        }
    }

    /**
     * The value of a row, in the colors of the selection while it is marked. The colors are looked up when it is
     * painted, so that they follow the theme; the grid never has the focus, so they are the ones of a focused list.
     */
    private static final class ValueLabel extends JLabel {
        private boolean marked;

        ValueLabel(String text) {
            setBorder(BorderFactory.createEmptyBorder(ROW_PAD, MARK_PAD, ROW_PAD, MARK_PAD));
            // values can be text from the file, the label must not render it as HTML when it gets it
            PortexSwingUtils.disableHtml(this);
            setText(text);
            setToolTipText(text);
        }

        void setMarked(boolean marked) {
            if (this.marked != marked) {
                this.marked = marked;
                repaint();
            }
        }

        boolean isMarked() {
            return marked;
        }

        @Override
        public Color getForeground() {
            Color selected = marked ? UIManager.getColor("List.selectionForeground") : null;
            return selected != null ? selected : super.getForeground();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color background = marked ? UIManager.getColor("List.selectionBackground") : null;
            if (background != null) {
                g.setColor(background);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
            super.paintComponent(g);
        }

        @Override
        public JToolTip createToolTip() {
            // values can be text from the file, e.g., the selected bytes as ASCII
            JToolTip tip = super.createToolTip();
            PortexSwingUtils.disableHtml(tip);
            return tip;
        }
    }
}
