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
package com.github.struppigel.gui.pedetails;

import com.github.katjahahn.parser.sections.*;
import com.github.struppigel.gui.FullPEData;
import com.github.struppigel.gui.PEFieldsTable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.github.katjahahn.parser.sections.SectionHeaderKey.*;
import static java.lang.Math.min;

/**
 * There can be many sections, so this panel adds tabs at the top.
 */
public class SectionsTabbedPanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private FullPEData peData;
    private final List<JTable> tables = new ArrayList<>();
    private final JTabbedPane tabbedPane = new JTabbedPane();

    private static final int SECTIONS_PER_TABLE = 4;
    private static final int TABLES_PER_TAB = 2;
    private static final int SECTION_NR_MAX = 200;
    private boolean hexEnabled = true;

    public SectionsTabbedPanel() {
        this.setLayout(new GridLayout(0,1));
        add(tabbedPane);
    }

    private void cleanUpTabsAndTables() {
        tabbedPane.removeAll();
        tables.clear();
        //tabs.clear(); // this will make everything non-working, but why?
        LOGGER.debug("Tabs and tables cleared");
    }

    public void setPeData(FullPEData peData){
        LOGGER.debug("PEData for tabbed Pane changed");
        this.peData = peData;
        initializeContent();
    }

    public void initializeContent() {
        LOGGER.debug("Init tabs for section tables");
        cleanUpTabsAndTables();

        SectionTable sectionTable  = peData.getPeData().getSectionTable();
        List<SectionHeader> sections = sectionTable.getSectionHeaders();
        List<JPanel> tabs = initTabs(sections);

        if (peData == null) {
            LOGGER.error("PE Data is null!");
            return;
        }
        initTables(tabs, sections);

        LOGGER.debug("Section table shown");
        refreshPanel(tabs);
    }

    private void initTables(List<JPanel> tabs, List<SectionHeader> sections) {
        // we need that to calculate how many tabs and tables
        // init counters
        int tableCount = 0;
        int tabIndex = 0;
        // get first tab
        JPanel currTab = tabs.get(tabIndex);
        // a section group will contain SECTIONS_PER_TABLE sections max
        List<SectionHeader> sectionGroup = new ArrayList<>();

        // we want to add every section
        for (SectionHeader currSec : sections) {
            // obtain current section header and add to section group
            sectionGroup.add(currSec);
            // check if enough sections to create a table
            if (sectionGroup.size() == SECTIONS_PER_TABLE) {
                // create table with the sections and add table to current tab
                addSingleTableForSections(sectionGroup, currTab);
                // increment table counter
                tableCount++;
                // we added the sections to a table, so empty the list
                sectionGroup.clear();
                // check if we need to grab the next tab for the next table
                if (tableCount % TABLES_PER_TAB == 0) {
                    // increment tab index (which is equal to tab count - 1)
                    tabIndex++;
                    // bounds check, in case we are already done with everything the index will be out of bounds
                    if (tabIndex < tabs.size()) {
                        currTab = tabs.get(tabIndex);
                    }
                }
            }
        }
        // add remaining tables
        if(sectionGroup.size() > 0) {
            addSingleTableForSections(sectionGroup, currTab);
            tableCount++;
        }
        LOGGER.debug("Added " + tableCount + " tables and " + tabIndex + " tabs");
    }

    private List<JPanel> initTabs(List<SectionHeader> sections) {
        int secNr = min(sections.size(), SECTION_NR_MAX);
        int nrOfTabs = new Double(Math.ceil(secNr/(double)(TABLES_PER_TAB * SECTIONS_PER_TABLE))).intValue();
        LOGGER.debug("Number of tabs to create: " + nrOfTabs);
        if(nrOfTabs == 0){ nrOfTabs = 1;}
        List<JPanel> tabs = new ArrayList<>();
        for (int i = 0; i < nrOfTabs; i++) {
            JPanel tab = new JPanel();
            tab.setLayout(new GridLayout(0, 1));
            tabbedPane.addTab((i + 1) + "", tab);
            tabs.add(tab);
        }
        return tabs;
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

    private void addSingleTableForSections(List<SectionHeader> sections, JPanel tab) {
        LOGGER.info("Setting hex enabled to " + hexEnabled);
        JTable table = new PEFieldsTable(hexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();

        createTableHeaderForSections(sections, model);
        createRowsForSections(sections, model);

        table.setModel(model);
        addTable(table, tab);
    }

    private void addTable(JTable table, JPanel tab) {
        tables.add(table);
        JScrollPane sPane = new JScrollPane(table);
        tab.add(sPane);
    }

    private void createRowsForSections(List<SectionHeader> sections, DefaultTableModel model) {
        // Section tables should only use string based sorting because there are mixed data types --> keep String[] type for rows
        List<String[]> rows = new ArrayList<>();
        SectionLoader loader = new SectionLoader(peData.getPeData());
        boolean lowAlign = peData.getPeData().getOptionalHeader().isLowAlignmentMode();

        // collect entropy
        Stream<String> entropyRow = sections.stream().map(s -> String.format("%1.2f", peData.getEntropyForSection(s.getNumber())));
        addStreamToRow(entropyRow, rows, "Entropy");

        // collect Pointer To Raw Data
        Stream<String> ptrToRawRow = sections.stream().map(s -> toHexIfEnabled(s.get(SectionHeaderKey.POINTER_TO_RAW_DATA)));
        addStreamToRow(ptrToRawRow, rows, "Pointer To Raw Data");

        // collect aligned Pointer To Raw Data

        Stream<String> alignedPtrRaw = sections.stream().map(s -> toHexIfEnabled(s.getAlignedPointerToRaw(lowAlign)));
        addStreamToRow(alignedPtrRaw, rows, "-> Aligned (act. start)");

        // collect Size of Raw Data
        Stream<String> sizeRawRow = sections.stream().map(s -> toHexIfEnabled(s.get(SIZE_OF_RAW_DATA)));
        addStreamToRow(sizeRawRow, rows, "Size Of Raw Data");

        // collect actual read size
        Stream<String> readSizeRow = sections.stream().map(s -> s.get(SIZE_OF_RAW_DATA) != loader.getReadSize(s) ? toHexIfEnabled(loader.getReadSize(s)) : "");
        addStreamToRow(readSizeRow, rows, "-> Actual Read Size");

        // collect Physical End
        Stream<String> endRow = sections.stream().map(s -> toHexIfEnabled(loader.getReadSize(s) + s.getAlignedPointerToRaw(lowAlign)));
        addStreamToRow(endRow, rows, "-> Physical End");

        // collect VA
        Stream<String> vaRow = sections.stream().map(s -> toHexIfEnabled(s.get(VIRTUAL_ADDRESS)));
        addStreamToRow(vaRow, rows, "Virtual Address");

        // collect alligned VA
        Stream<String> vaAlignedRow = sections.stream().map(s -> s.get(VIRTUAL_ADDRESS) != s.getAlignedVirtualAddress(lowAlign) ? toHexIfEnabled(s.getAlignedVirtualAddress(lowAlign)) : "");
        addStreamToRow(vaAlignedRow, rows, "-> Aligned");

        // collect Virtual Size
        Stream<String> vSizeRow = sections.stream().map(s -> toHexIfEnabled(s.get(VIRTUAL_SIZE)));
        addStreamToRow(vSizeRow, rows, "Virtual Size");
        // collect Actual Virtual Size
        Stream<String> actSizeRow = sections.stream().map(s -> s.get(VIRTUAL_SIZE) != loader.getActualVirtSize(s) ? toHexIfEnabled(loader.getActualVirtSize(s)) : "");
        addStreamToRow(actSizeRow, rows, "-> Actual Virtual Size");
        // collect Virtual End
        Stream<String> veRow = sections.stream().map(s -> toHexIfEnabled(s.getAlignedVirtualAddress(lowAlign) + loader.getActualVirtSize(s)));
        addStreamToRow(veRow, rows, "-> Virtual End");
        // collect Pointer To Relocations
        Stream<String> relocRow = sections.stream().map(s -> toHexIfEnabled(s.get(POINTER_TO_RELOCATIONS)));
        addStreamToRow(relocRow, rows, "Pointer To Relocations");
        // collect Number Of Relocations
        Stream<String> numreRow = sections.stream().map(s -> toHexIfEnabled(s.get(NUMBER_OF_RELOCATIONS)));
        addStreamToRow(numreRow, rows, "Number Of Relocations");
        // collect Pointer To Line Numbers
        Stream<String> linRow = sections.stream().map(s -> toHexIfEnabled(s.get(POINTER_TO_LINE_NUMBERS)));
        addStreamToRow(linRow, rows, "Pointer To Line Numbers");
        // collect Number Of Line Numbers
        Stream<String> numLinRow = sections.stream().map(s -> toHexIfEnabled(s.get(NUMBER_OF_LINE_NUMBERS)));
        addStreamToRow(numLinRow, rows, "Number Of Line Numbers");

        for(SectionCharacteristic ch : SectionCharacteristic.values()) {
            Stream<String> secCharRow = sections.stream().map(s -> s.getCharacteristics().contains(ch) ? "x" : "");
            addStreamToRow(secCharRow, rows, ch.shortName());
        }

        // add all rows to model
        for(String[] row : rows) {
            model.addRow(row);
        }
    }

    private void addStreamToRow(Stream<String> aStream, List<String[]> rows, String title) {
        List<String> list = aStream.collect(Collectors.toList());
        boolean hasContent = false;
        for(String l : list) {
            if (!l.equals("")) {
                hasContent = true;
                break;
            }
        }
        if(hasContent) {
            list.add(0, title);
            rows.add(list.toArray(new String[0]));
        }
    }

    private String toHexIfEnabled(Long num) {
        if(hexEnabled) {
            return "0x" + Long.toHexString(num);
        }
        return num.toString();
    }

    private void createTableHeaderForSections(List<SectionHeader> sections, DefaultTableModel model) {
        List<String> names = sections.stream().map(h -> h.getNumber() + ". " + h.getName()).collect(Collectors.toList());
        names.add(0,"");
        String[] tableHeader = names.toArray(new String[0]);
        model.setColumnIdentifiers(tableHeader);
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        if(peData == null) {return;}
        initializeContent();
    }
}
