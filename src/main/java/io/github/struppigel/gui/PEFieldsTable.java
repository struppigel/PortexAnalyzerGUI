/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
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
package io.github.struppigel.gui;

import io.github.struppigel.gui.pedetails.FileContentPreviewPanel;
import io.github.struppigel.gui.utils.PortexSwingUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Vector;

public class PEFieldsTable extends JTable {

    private static final Logger LOGGER = LogManager.getLogger();
    // longer tool tips wrap at this width
    private static final int TOOL_TIP_WIDTH = 600;
    // text that is this close to the width of a wrapped cell is measured exactly
    private static final int WRAP_SLACK = 4;
    private final boolean enableHex;
    private final WrappingRenderer wrappingRenderer = new WrappingRenderer();
    private int previewOffsetColumn;
    private int wrappedColumn = -1;

    public PEFieldsTable(boolean enableHex) {
        this.enableHex = enableHex;
        initTable();
    }

    public void setPreviewOffsetColumn(int column, FileContentPreviewPanel previewPanel) {
        this.previewOffsetColumn = column;
        initOffsetListener(this, previewPanel);
    }

    /**
     * Wraps the text of the column at its width, the rows grow to show all of it, e.g., for long descriptions of
     * anomalies.
     *
     * @param modelColumn index of the column in the model
     */
    public void setWrappedColumn(int modelColumn) {
        this.wrappedColumn = modelColumn;
        resizeAndRepaint();
    }

    @Override
    public TableCellRenderer getCellRenderer(int row, int column) {
        if (wrappedColumn >= 0 && convertColumnIndexToModel(column) == wrappedColumn) {
            return wrappingRenderer;
        }
        Object value = getValueAt(row, column);
        // columns that mix numbers and text have the class Object, e.g., offsets and "" for entries without one
        if (value instanceof Long || value instanceof Integer) {
            return getDefaultRenderer(value.getClass());
        }
        return super.getCellRenderer(row, column);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        // before the renderer gets the text of the cell, which can come from the file
        if (renderer instanceof JComponent) {
            PortexSwingUtils.disableHtml((JComponent) renderer);
        }
        return super.prepareRenderer(renderer, row, column);
    }

    @Override
    public void doLayout() {
        super.doLayout();
        updateRowHeights();
    }

    /**
     * Sets the height of each row so that the text of the wrapped column fits. Rows whose text fits on one line are
     * one line high.
     */
    private void updateRowHeights() {
        if (wrappedColumn < 0 || getRowCount() == 0) return;
        int column = convertColumnIndexToView(wrappedColumn);
        if (column < 0) return;
        // measured where it is painted, so that the text is laid out for the same screen, e.g., with HiDPI scaling
        CellRendererPane pane = rendererPane();
        boolean added = pane != null && wrappingRenderer.getParent() != pane;
        if (added) pane.add(wrappingRenderer);
        try {
            updateRowHeights(column);
        } finally {
            if (added) pane.remove(wrappingRenderer);
        }
    }

    private CellRendererPane rendererPane() {
        for (Component child : getComponents()) {
            if (child instanceof CellRendererPane) return (CellRendererPane) child;
        }
        return null;
    }

    private void updateRowHeights(int column) {
        // the border and font of the cells are known once the renderer is prepared
        prepareRenderer(wrappingRenderer, 0, column);
        Insets insets = wrappingRenderer.getInsets();
        int textWidth = getCellRect(0, column, false).width - insets.left - insets.right;
        FontMetrics metrics = wrappingRenderer.getFontMetrics(wrappingRenderer.getFont());
        // at least one line of wrapped text, which is not centered like the text of the other cells
        int minHeight = Math.max(getRowHeight(), metrics.getHeight() + insets.top + insets.bottom);
        for (int row = 0; row < getRowCount(); row++) {
            Object value = getValueAt(row, column);
            String text = value == null ? "" : value.toString();
            int height = minHeight;
            if (text.indexOf('\n') >= 0 || metrics.stringWidth(text) + WRAP_SLACK > textWidth) {
                Component cell = prepareRenderer(wrappingRenderer, row, column);
                height = Math.max(height, cell.getPreferredSize().height);
            }
            if (getRowHeight(row) != height) {
                setRowHeight(row, height);
            }
        }
    }

    /**
     * Shows the whole text of cells that cut it off.
     */
    @Override
    public String getToolTipText(MouseEvent event) {
        int row = rowAtPoint(event.getPoint());
        int column = columnAtPoint(event.getPoint());
        if (row >= 0 && column >= 0) {
            Component cell = prepareRenderer(getCellRenderer(row, column), row, column);
            if (cell instanceof JLabel && cell.getPreferredSize().width > getCellRect(row, column, false).width) {
                return toolTipFor(((JLabel) cell).getText());
            }
        }
        return super.getToolTipText(event);
    }

    private String toolTipFor(String text) {
        // escaped, so that text from the file is never interpreted as HTML
        String html = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        if (getFontMetrics(getFont()).stringWidth(text) > TOOL_TIP_WIDTH) {
            return "<html><body style='width: " + TOOL_TIP_WIDTH + "px'>" + html + "</body></html>";
        }
        return "<html>" + html + "</html>";
    }

    private void initTable() {
        // set PETableModel for proper sorting of integers and longs
        DefaultTableModel model = new PETableModel();
        setModel(model);

        // show long and int as hexadecimal string
        setDefaultRenderer(Long.class, new HexValueRenderer(enableHex));
        setDefaultRenderer(Integer.class, new HexValueRenderer(enableHex));

        setPreferredScrollableViewportSize(new Dimension(500, 70));
        setFillsViewportHeight(true);
        setAutoCreateRowSorter(true);
        setDefaultEditor(Object.class, null); // make not editable
    }

    private void initOffsetListener(JTable table, FileContentPreviewPanel previewPanel) {
        ListSelectionModel model = table.getSelectionModel();
        model.addListSelectionListener(e -> {
            Long offset = getOffsetForSelectedRow(table);
            if(offset == null) return;
            LOGGER.info(offset + " offset selected");
            previewPanel.showContentAtOffset(offset);
        });
    }

    private Long getOffsetForSelectedRow(JTable table) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        int row = getSelectedRow(table);
        if (row == -1) return null;
        Vector vRow = (Vector) model.getDataVector().elementAt(row);
        Object offset = vRow.elementAt(previewOffsetColumn);
        // rows without offset, e.g., "no matches found"
        if (!(offset instanceof Long)) return null;
        return (Long) offset;
    }

    /**
     * Return a vector of the data in the currently selected row. Returns null if no row selected
     *
     * @param table
     * @return vector of the data in the currently selected row or null if nothing selected
     */
    private int getSelectedRow(JTable table) {
        int rowIndex = table.getSelectedRow();
        if (rowIndex >= 0) {
            return table.convertRowIndexToModel(rowIndex);

        }
        return -1;
    }

    public static class PETableModel extends DefaultTableModel {

        // cached because the column class is requested for every rendered cell
        private Class<?>[] columnClasses;

        @Override
        public void fireTableChanged(TableModelEvent e) {
            columnClasses = null;
            super.fireTableChanged(e);
        }

        /**
         * Returns the class that all values of the column have, so that sorting compares values of the same type.
         * Returns Object.class if the column has no values or values of different types, e.g., Integer hints and
         * empty strings in the imports table.
         */
        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex < 0 || columnIndex >= getColumnCount()) {
                return Object.class;
            }
            if (columnClasses == null || columnClasses.length != getColumnCount()) {
                columnClasses = determineColumnClasses();
            }
            return columnClasses[columnIndex];
        }

        private Class<?>[] determineColumnClasses() {
            Class<?>[] classes = new Class<?>[getColumnCount()];
            for (int column = 0; column < classes.length; column++) {
                Class<?> common = null;
                for (int row = 0; row < getRowCount(); row++) {
                    Object value = getValueAt(row, column);
                    if (value == null) continue;
                    if (common == null) {
                        common = value.getClass();
                    } else if (common != value.getClass()) {
                        common = Object.class;
                        break;
                    }
                }
                classes[column] = common == null ? Object.class : common;
            }
            return classes;
        }
    }

    public static class HexValueRenderer extends DefaultTableCellRenderer {

        private boolean enableHex;

        public HexValueRenderer(boolean enableHex){
            this.enableHex = enableHex;
        }
        @Override
        public void setValue(Object value){
            if(value == null || (value.getClass() != Long.class && value.getClass() != Integer.class)) {
                // the renderer is reused for all cells, so the text must always be set
                super.setValue(value);
                return;
            }
            if(value.getClass() == Long.class) {
                Long lvalue = (Long) value;
                if(enableHex) {
                    setText(toHex(lvalue));
                } else {
                    setText(lvalue.toString());
                }
            }
            if(value.getClass() == Integer.class) {
                Integer ivalue = (Integer) value;
                if(enableHex) {
                    setText(toHex(ivalue));
                } else {
                    setText(ivalue.toString());
                }
            }
        }
    }

    /**
     * Shows the text of a cell on as many lines as the width of the column needs. The table sets the row heights to
     * fit, see {@link #setWrappedColumn(int)}.
     */
    static class WrappingRenderer extends JTextArea implements TableCellRenderer {

        WrappingRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            // the usual renderer of the cell knows the look and feel, e.g., the colors of selected and alternating rows
            TableCellRenderer usual = table.getDefaultRenderer(table.getColumnClass(column));
            if (usual instanceof JComponent) {
                PortexSwingUtils.disableHtml((JComponent) usual);
            }
            Component look = usual.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setBackground(look.getBackground());
            setForeground(look.getForeground());
            setFont(look.getFont());
            setBorder(look instanceof JComponent ? ((JComponent) look).getBorder() : null);
            setText(value == null ? "" : value.toString());
            // the text area computes its preferred height for this width
            setSize(table.getCellRect(row, column, false).width, Short.MAX_VALUE);
            return this;
        }
    }

    private static String toHex(Long num) {
        return "0x" + Long.toHexString(num);
    }

    private static String toHex(Integer num) {
        return "0x" + Integer.toHexString(num);
    }
}
