/**
 * *****************************************************************************
 * Copyright 2023 Karsten Hahn
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
package com.github.struppigel.gui.pedetails;

import com.github.struppigel.parser.StandardField;
import com.github.struppigel.gui.PEFieldsTable;
import com.github.struppigel.gui.utils.TableContent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TabbedPanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private final List<JTable> tables = new ArrayList<>();
    private final JTabbedPane tabbedPane = new JTabbedPane();
    private final FileContentPreviewPanel previewPanel;
    private List<String> tableHeader = new ArrayList<>();
    private List<TableContent> contents = new ArrayList<>();
    private boolean hexEnabled = true;

    public TabbedPanel(FileContentPreviewPanel previewPanel) {
        LOGGER.debug("Tabbed Panel constructor");
        this.previewPanel = previewPanel;
        this.setLayout(new GridLayout(0,1));
        add(tabbedPane);
    }

    public void setContent(List<TableContent> contents, String[] tableHeader) {
        this.contents = contents;
        this.tableHeader = Arrays.asList(tableHeader);
        initializeContent();
    }

    private void cleanUpTabsAndTables() {
        tabbedPane.removeAll();
        tables.clear();
        LOGGER.debug("Tabs and tables cleared");
    }

    public void initializeContent() {
        LOGGER.debug("Init tabs for tables");
        cleanUpTabsAndTables();
        List<JPanel> tabs = initTabsAndTables();
        refreshPanel(tabs);
    }

    private void refreshPanel(List<JPanel> tabs) {
        LOGGER.debug("Refreshing tabs");
        for(JTable tbl : tables) {
            tbl.revalidate();
            tbl.repaint();
        }
        for(JPanel tab : tabs) {
            tab.revalidate();
            tab.repaint();
        }
        revalidate();
        repaint();
        tabbedPane.revalidate();
        tabbedPane.repaint();
    }

    private String toHexIfEnabled(Long num) {
        if(hexEnabled) {
            return "0x" + Long.toHexString(num);
        }
        return num.toString();
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        initializeContent();
    }

    private List<JPanel> initTabsAndTables() {
        List<JPanel> tabs = new ArrayList<>();
        for(TableContent content : contents) {
            String title = content.getTitle();
            JPanel tab = createTabWithTitle(title);
            tabs.add(tab);
            addSingleTableForContent(content, tab);
        }
        return tabs;
    }

    private JPanel createTabWithTitle(String title) {
        JPanel tab = new JPanel();
        tab.setLayout(new GridLayout(0, 1));
        tabbedPane.addTab(title, tab);
        return tab;
    }

    private void addSingleTableForContent(TableContent content, JPanel tab) {
        LOGGER.info("Setting hex enabled to " + hexEnabled);
        PEFieldsTable table = new PEFieldsTable(hexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        table.setPreviewOffsetColumn(2, previewPanel);
        // add table header
        String[] header = tableHeader.toArray(new String[0]);
        model.setColumnIdentifiers(header);
        createRows(content, model);

        table.setModel(model);
        addTableAndDescription(table, tab, content.getDescription());
    }
    private void createRows(TableContent content, DefaultTableModel model) {
        for(StandardField field : content) {
            Object[] row = {field.getDescription(), field.getValue(), field.getOffset()};
            model.addRow(row);
        }
    }

    private void addTableAndDescription(JTable table, JPanel tab, String description) {
        tables.add(table);
        if(description.length() > 0) {
            tab.add(new JScrollPane(new JTextArea(description)));
        }
        JScrollPane sPane = new JScrollPane(table);
        tab.add(sPane);
    }

}
