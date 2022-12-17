/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui;

import com.github.katjahahn.parser.RichHeader;
import com.github.katjahahn.parser.StandardField;
import com.github.katjahahn.parser.coffheader.COFFFileHeader;
import com.github.katjahahn.parser.msdos.MSDOSHeader;
import com.github.katjahahn.parser.optheader.DataDirEntry;
import com.github.katjahahn.parser.optheader.OptionalHeader;
import com.github.katjahahn.parser.sections.SectionHeader;
import com.github.katjahahn.parser.sections.SectionLoader;
import com.github.katjahahn.parser.sections.SectionTable;
import com.google.common.base.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.github.katjahahn.parser.optheader.StandardFieldEntryKey.ADDR_OF_ENTRY_POINT;

public class PEDetailsPanel extends JPanel {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String NL = System.getProperty("line.separator");
    private FullPEData peData;

    /**
     * Part of table panel
     */
    private final List<JTable> tables = new ArrayList<>();
    private final JTextArea descriptionField = new JTextArea();

    /**
     * Contains the tablePanel, tabbedPanel and descriptionField
     */
    private final JPanel cardPanel = new JPanel(new CardLayout());

    /**
     * Contains the tables
     */
    private final JPanel tablePanel = new JPanel();
    private SectionsTabbedPanel tabbedPanel;
    //private VisualizerPanel visPanel;

    public PEDetailsPanel() {
        super(new GridLayout(1, 0));
        initDetails();
    }

    private void initDetails() {
        JScrollPane scrollPaneDescription = new JScrollPane(descriptionField);
        descriptionField.setText("Drop file here");
        descriptionField.setEditable(false);
        descriptionField.setDragEnabled(true);
        descriptionField.setLineWrap(true);

        //this.visPanel = new VisualizerPanel(true, true, true, 50);  // Will do this later

        tabbedPanel = new SectionsTabbedPanel();
        tablePanel.setLayout(new GridLayout(0, 1));

        cardPanel.add(tablePanel, "TABLE");
        cardPanel.add(scrollPaneDescription, "DESCRIPTION");
        cardPanel.add(tabbedPanel, "TABBED");
        //cardPanel.add(visPanel, "VISUALIZATION"); // Will do this later


        //add the table to the frame
        setLayout(new BorderLayout());
        add(cardPanel, BorderLayout.CENTER);
        showDescriptionPanel();
    }

    public static JTable createEmptyTable() { // TODO make this a Table class instead!
        DefaultTableModel model = new DefaultTableModel();
        JTable table = new JTable(model);
        table.setCellSelectionEnabled(true);
        table.setPreferredScrollableViewportSize(new Dimension(500, 70));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setDefaultEditor(Object.class, null); // make not editable
        return table;
    }

    public void setPeData(FullPEData peData) {
        this.peData = peData;
        tabbedPanel.setPeData(peData);
        /*
        try {
            visPanel.visualizePE(peData.getFile());
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e);
        }*/
    }

    public void showDosStub() {
        if (peData != null) {
            MSDOSHeader header = peData.getPeData().getMSDOSHeader();
            List<StandardField> entries = header.getHeaderEntries();
            String[] tableHeader = {"Description", "Value", "Value offset"};
            showFieldEntries(entries, tableHeader);
            LOGGER.debug("MS DOS Stub shown");
            showTablePanel();
        } else {
            LOGGER.warn("PE Data is null!");
        }
    }

    public void showCoffFileHeader() {
        if (peData == null) return;
        COFFFileHeader header = peData.getPeData().getCOFFFileHeader();
        List<StandardField> entries = header.getHeaderEntries();
        String date = header.getTimeDate().toString();
        String text = "Time date stamp : " + date + NL;
        text += "Machine type: " + header.getMachineType().getDescription() + NL;
        text += "Characteristics: " + NL;
        text += header.getCharacteristics().stream().map(ch -> "\t* " + ch.getDescription()).collect(Collectors.joining(NL));
        String[] tableHeader = {"Description", "Value", "Value offset"};
        showFieldEntriesAndDescription(entries, tableHeader, text);
        LOGGER.debug("COFF File header shown");
        showTablePanel();
    }

    public void showOptionalHeader() {
        showEmpty();
    }

    private void showEmpty() {
        if (peData != null) {
            descriptionField.setText("");
            descriptionField.repaint();
            showDescriptionPanel();
        } else {
            LOGGER.warn("PE Data is null!");
        }
    }

    public void showStandardFieldsTable() {
        if (peData == null) return;
        OptionalHeader header = peData.getPeData().getOptionalHeader();
        List<StandardField> entries = new ArrayList<>(header.getStandardFields().values());
        String[] tableHeader = {"Description", "Value", "Value offset"};
        String text = "Linker version: " + header.getLinkerVersionDescription() + NL;
        text += "Magic Number: " + header.getMagicNumber().getDescription() + NL;
        showFieldEntriesAndDescription(entries, tableHeader, text);
        LOGGER.debug("Standard Fields shown");
        showTablePanel();

    }

    public void showWindowsFieldsTable() {
        if (peData == null) return;
        OptionalHeader header = peData.getPeData().getOptionalHeader();
        List<StandardField> entries = new ArrayList<>(header.getWindowsSpecificFields().values());
        String[] tableHeader = {"Description", "Value", "Value offset"};

        String text = "Subsystem: " + header.getSubsystem().getDescription() + NL;

        long entryPoint = header.get(ADDR_OF_ENTRY_POINT);
        Optional<SectionHeader> maybeHeader = new SectionLoader(peData.getPeData()).maybeGetSectionHeaderByRVA(entryPoint);
        if (maybeHeader.isPresent()) {
            text += "Entry point is in section " + maybeHeader.get().getNumber() + " with name " + maybeHeader.get().getName() + NL;
        } else {
            text += "Entry point is not in a section" + NL;
        }

        text += NL + "DLL Characteristics:" + NL;
        text += header.getDllCharacteristics().stream().map(ch -> "\t* " + ch.getDescription()).collect(Collectors.joining(NL)) + NL;

        showFieldEntriesAndDescription(entries, tableHeader, text);
        LOGGER.debug("Windows Fields shown");
        showTablePanel();
    }

    public void showDataDirectoryTable() {
        if (peData == null) return;
        OptionalHeader header = peData.getPeData().getOptionalHeader();
        SectionTable secTable = peData.getPeData().getSectionTable();
        List<String[]> dirEntries = new ArrayList<>();
        for (DataDirEntry de : header.getDataDirectory().values()) {
            String offset = toHex(de.getFileOffset(secTable));
            String size = toHex(de.getDirectorySize());
            String va = toHex(de.getVirtualAddress());
            Optional<SectionHeader> hOpt = de.maybeGetSectionTableEntry(secTable);
            String section = hOpt.isPresent() ? hOpt.get().getNumber() + ". " + hOpt.get().getName() : "NaN";
            String valueOffset = toHex(de.getTableEntryOffset());
            String[] row = {de.getKey().toString(), va, offset, size, section, valueOffset};
            dirEntries.add(row);
        }
        String[] tableHeader = {"Data directory", "RVA", "-> Offset", "Size", "In section", "Value offset"};
        showTextEntries(dirEntries, tableHeader);
        LOGGER.debug("Windows Fields shown");
        showTablePanel();
    }

    @Override
    public synchronized void setDropTarget(DropTarget dt) {
        descriptionField.setDropTarget(dt);
        tablePanel.setDropTarget(dt);
        tabbedPanel.setDropTarget(dt);
    }

    private String toHex(Long num) {
        return "0x" + Long.toHexString(num);
    }

    private void showDescriptionPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "DESCRIPTION");
        LOGGER.debug("Card panel set to DESCRIPTION");
    }

    private void showTablePanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "TABLE");
        LOGGER.debug("Card panel set to TABLE");
    }

    private void showTabbedPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "TABBED");
        LOGGER.debug("Card panel set to TABBED");
    }

    public void showSectionTable() {
        showTabbedPanel();
    }

    private void addTable(JTable table) {
        tables.add(table);
        JScrollPane sPane = new JScrollPane(table);
        tablePanel.add(sPane);
    }

    private void cleanUpTablePanel() {
        tables.clear();
        tablePanel.removeAll();
    }

    private void showUpdatesInTablePanel() {
        for (JTable tbl : tables) {
            tbl.revalidate();
            tbl.repaint();
        }
        tablePanel.revalidate();
        tablePanel.repaint();
    }

    private void showFieldEntries(List<StandardField> entries, String[] tableHeader) {
        cleanUpTablePanel();
        JTable table = createEmptyTable();
        DefaultTableModel model = new DefaultTableModel();
        model.setColumnIdentifiers(tableHeader);
        for (StandardField field : entries) {
            String[] row = {field.getDescription(), "0x" + Long.toHexString(field.getValue()), "0x" + Long.toHexString(field.getOffset())};
            model.addRow(row);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private void showFieldEntriesAndDescription(List<StandardField> entries, String[] tableHeader, String text) {
        cleanUpTablePanel();
        tablePanel.add(new JScrollPane(new JTextArea(text)));
        JTable table = createEmptyTable();
        DefaultTableModel model = new DefaultTableModel();
        model.setColumnIdentifiers(tableHeader);
        for (StandardField field : entries) {
            String[] row = {field.getDescription(), "0x" + Long.toHexString(field.getValue()), "0x" + Long.toHexString(field.getOffset())};
            model.addRow(row);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private void showTextEntries(List<String[]> entries, String[] tableHeader) {
        cleanUpTablePanel();
        JTable table = createEmptyTable();
        DefaultTableModel model = new DefaultTableModel();
        model.setColumnIdentifiers(tableHeader);
        model.setColumnIdentifiers(tableHeader);
        for (String[] row : entries) {
            model.addRow(row);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private void showTextEntriesAndDescription(List<String[]> entries, String[] tableHeader, String text) {
        cleanUpTablePanel();
        JTextArea t = new JTextArea(text); // if you end up using this again, create a class instead
        t.setEditable(false);
        t.setDragEnabled(true);
        t.setLineWrap(true);
        tablePanel.add(new JScrollPane(t));
        JTable table = createEmptyTable();
        DefaultTableModel model = new DefaultTableModel();
        model.setColumnIdentifiers(tableHeader);
        model.setColumnIdentifiers(tableHeader);
        for (String[] row : entries) {
            model.addRow(row);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    public void showPEHeaders() {
        descriptionField.setText("");
        showDescriptionPanel();
    }

    public void showOverlay() {
        if (peData == null) return;
        try {
            long offset = peData.getOverlay().getOffset();
            long size = peData.getOverlay().getSize();
            double entropy = peData.getOverlayEntropy() * 8;
            List<String> sigs = peData.getOverlaySignatures();

            String text = "Offset: " + toHex(offset) + NL + "Size: " + toHex(size) + NL;
            String packed = entropy >= 7.0 ? " (packed)" : "";
            text += "Entropy: " + String.format("%1.2f", entropy) + packed + NL + NL;
            text += "Signatures: " + NL;
            for (String s : sigs) {
                text += s + NL;
            }

            descriptionField.setText(text);
            showDescriptionPanel();
        } catch (IOException e) {
            String message = "Could not read Overlay! Reason: " + e.getMessage();
            LOGGER.error(e);
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    message,
                    "Overlay reading error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void showRichHeader() {
        if (peData == null) return;
        if (peData.getPeData().maybeGetRichHeader().isPresent()) {
            RichHeader rich = peData.getPeData().maybeGetRichHeader().get();
            List<RichHeader.RichEntry> richEntries = rich.getRichEntries();
            byte[] xorKey = rich.getXORKey();
            String xorString = toHex(xorKey);
            String checkSumStr = rich.isValidChecksum() ? "Checksum is valid" : "Checksum is invalid!";

            String text = "XOR key: " + xorString + NL + checkSumStr + NL + NL;

            List<String> formats = rich.getKnownFormats();
            if (formats.size() > 0) {
                text += "XOR key known to be emitted by: " + formats.stream().collect(Collectors.joining(", ")) + NL + NL;
            }
            List<String[]> entries = richEntries.stream()
                    .map(e -> new String[]{e.getProductIdStr(), e.getBuildStr(), e.count() + ""})
                    .collect(Collectors.toList());
            String[] tableHeader = {"Product id", "Build id", "File count"};
            showTextEntriesAndDescription(entries, tableHeader, text);
            showTablePanel();
        }
    }

    private String toHex(byte[] xorKey) {
        String result = "";
        for (byte b : xorKey) {
            result += "0x" + String.format("%02X ", b);
        }
        return result;
    }

    public void showManifest() {
        if (peData == null) return;
        descriptionField.setText(peData.getManifest());
        showDescriptionPanel();
    }

    public void showVersionInfo() {
        if (peData == null) return;
        descriptionField.setText(peData.getVersionInfo());
        showDescriptionPanel();
    }

    public void showResources() {
        if (peData == null) return;
        String[] tableHeader = {"Type", "Name", "Language", "Offset", "Size", "Signatures"};
        showTextEntries(peData.getResourceTableEntries(), tableHeader);
        showTablePanel();

    }

    public void showImports() {
        if (peData == null) return;
        // Make beautiful with tabs
        String[] tableHeader = {"DLL", "Category", "Name", "Description", "RVA", "Hint"};
        showTextEntries(peData.getImportTableEntries(), tableHeader);
        showTablePanel();
    }

    public void showExports() {
        if (peData == null) return;
        // Make beautiful with tabs
        String[] tableHeader = {"Name", "Ordinal", "Symbol RVA", "Forwarder"};
        showTextEntries(peData.getExportTableEntries(), tableHeader);
        showTablePanel();
    }

    public void showDebugInfo() {
        if (peData == null) return;
        descriptionField.setText(peData.getDebugInfo());
        showDescriptionPanel();
    }

    public void showAnomalies() {
        if (peData == null) return;
        descriptionField.setText(peData.getAnomalyReport());
        showDescriptionPanel();
    }

    public void showHashes() {
        if (peData == null) return;
        
        String[] tableHeader = {"Section", "Type", "Hash value"};
        List<String[]> entries = peData.getSectionHashTableEntries();
        String text = peData.getHashesReport();
        showTextEntriesAndDescription(entries, tableHeader, text);
        showTablePanel();
    }

    public void showVisualization() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "VISUALIZATION");
        LOGGER.debug("Card panel set to VISUALIZATION");
    }

    public void showPEFormat() {
        showEmpty();
    }
}
