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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

public class PEFieldsTable extends JTable {

    private static final Logger LOGGER = LogManager.getLogger();
    private final boolean enableHex;

    public PEFieldsTable(boolean enableHex) {
        this.enableHex = enableHex;
        initTable();
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
