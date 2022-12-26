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
package com.github.struppigel.gui;

import com.github.struppigel.gui.pedetails.FileContentPreviewPanel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Vector;

public class PEFieldsTable extends JTable {

    private static final Logger LOGGER = LogManager.getLogger();
    private final boolean enableHex;
    private int previewOffsetColumn;

    public PEFieldsTable(boolean enableHex) {
        this.enableHex = enableHex;
        initTable();
    }

    public void setPreviewOffsetColumn(int column, FileContentPreviewPanel previewPanel) {
        this.previewOffsetColumn = column;
        initOffsetListener(this, previewPanel);
    }

    private void initTable() {
        // set PETableModel for proper sorting of integers and longs
        DefaultTableModel model = new PETableModel();
        setModel(model);

        // show long and int as hexadecimal string
        setDefaultRenderer(Long.class, new HexValueRenderer(enableHex));
        setDefaultRenderer(Integer.class, new HexValueRenderer(enableHex));

        setCellSelectionEnabled(true);
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

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (this.getColumnCount() < columnIndex || this.getRowCount() == 0) {
                return Object.class;
            }
            Class clazz = getValueAt(0, columnIndex).getClass();
            return clazz;
        }
    }

    public static class HexValueRenderer extends DefaultTableCellRenderer {

        private boolean enableHex;

        public HexValueRenderer(boolean enableHex){
            this.enableHex = enableHex;
        }
        @Override
        public void setValue(Object value){
            if(value == null) {
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

    private static String toHex(Long num) {
        return "0x" + Long.toHexString(num);
    }

    private static String toHex(Integer num) {
        return "0x" + Integer.toHexString(num);
    }
}
