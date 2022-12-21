/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui.pedetails;

import com.github.katjahahn.parser.RichHeader;
import com.github.katjahahn.parser.StandardField;
import com.github.katjahahn.parser.coffheader.COFFFileHeader;
import com.github.katjahahn.parser.msdos.MSDOSHeader;
import com.github.katjahahn.parser.optheader.DataDirEntry;
import com.github.katjahahn.parser.optheader.OptionalHeader;
import com.github.katjahahn.parser.sections.SectionHeader;
import com.github.katjahahn.parser.sections.SectionLoader;
import com.github.katjahahn.parser.sections.SectionTable;
import com.github.struppigel.gui.FullPEData;
import com.github.struppigel.gui.MainFrame;
import com.github.struppigel.gui.PEFieldsTable;
import com.github.struppigel.gui.VisualizerPanel;
import com.github.struppigel.gui.pedetails.signatures.SignaturesPanel;
import com.github.struppigel.gui.utils.PortexSwingUtils;
import com.github.struppigel.settings.PortexSettings;
import com.google.common.base.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.github.katjahahn.parser.optheader.StandardFieldEntryKey.ADDR_OF_ENTRY_POINT;

/**
 * Displays the data in the middle.
 */
public class PEDetailsPanel extends JPanel {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String NL = System.getProperty("line.separator");
    private final VisualizerPanel rightPanel;
    private final MainFrame parent;
    private final PortexSettings settings;
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
    private VisualizerPanel visPanel = new VisualizerPanel(true, true, true, 180);
    ;
    private IconPanel iconPanel = new IconPanel();
    private final SignaturesPanel signaturesPanel;
    private boolean isHexEnabled = true;

    public PEDetailsPanel(VisualizerPanel visualizerPanel, MainFrame mainFrame, PortexSettings settings) {
        super(new GridLayout(1, 0));
        this.rightPanel = visualizerPanel;
        this.parent = mainFrame;
        this.settings = settings;
        signaturesPanel = new SignaturesPanel(settings);
        initDetails();
    }

    private void initDetails() {
        JScrollPane scrollPaneDescription = new JScrollPane(descriptionField);
        descriptionField.setText("Drop file here");
        descriptionField.setEditable(false);
        descriptionField.setDragEnabled(true);
        descriptionField.setLineWrap(true);

        // visualizer page + button
        JPanel bigVisuals = new JPanel();
        bigVisuals.setLayout(new BorderLayout());
        bigVisuals.add(visPanel, BorderLayout.CENTER);

        JPanel visButtonPanel = initSaveVisualsButtonPanel();
        bigVisuals.add(visButtonPanel, BorderLayout.PAGE_END);

        // icons page + button
        JPanel iconWrapperPanel = new JPanel();
        JPanel icoButtonPanel = initSaveIconsButtonPanel();
        iconWrapperPanel.setLayout(new BorderLayout());
        iconWrapperPanel.add(new JScrollPane(iconPanel), BorderLayout.CENTER);
        iconWrapperPanel.add(icoButtonPanel, BorderLayout.PAGE_END);

        tabbedPanel = new SectionsTabbedPanel();
        tablePanel.setLayout(new GridLayout(0, 1));

        cardPanel.add(tablePanel, "TABLE");
        cardPanel.add(scrollPaneDescription, "DESCRIPTION");
        cardPanel.add(tabbedPanel, "TABBED");
        cardPanel.add(bigVisuals, "VISUALIZATION");
        cardPanel.add(iconWrapperPanel, "ICONS");
        cardPanel.add(signaturesPanel, "SIGNATURES");

        //add the table to the frame
        setLayout(new BorderLayout());
        add(cardPanel, BorderLayout.CENTER);

        showDescriptionPanel();
    }

    private JPanel initSaveIconsButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton saveImgButton = new JButton("Save all");
        saveImgButton.addActionListener(e -> {
            String path = PortexSwingUtils.getSaveFolderNameFromUser(this);
            new SaveIconsWorker(path, iconPanel.getIcons()).execute();
        });

        buttonPanel.add(saveImgButton);
        return buttonPanel;
    }

    private class SaveIconsWorker extends SwingWorker<Boolean, Void> {
        private final List<BufferedImage> icons;
        private final String folder;

        public SaveIconsWorker(String folder, List<BufferedImage> icons) {
            this.folder = folder;
            this.icons = icons;
        }

        @Override
        protected Boolean doInBackground() {
            int counter = 0;
            boolean successFlag = true;
            for (BufferedImage icon : icons) {
                File file;
                // find next file path that does not exist
                do {
                    file = Paths.get(folder, counter + ".png").toFile();
                    counter++;
                } while (file.exists());
                try {
                    ImageIO.write(icon, "png", file);
                } catch (IOException e) {
                    LOGGER.error(e);
                    successFlag = false;
                }
            }
            return successFlag;
        }

        @Override
        protected void done() {
            try {
                Boolean success = get();
                if (success) {
                    JOptionPane.showMessageDialog(null,
                            "Icons successfully saved",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Unable to save some icons :(",
                            "Something went wrong",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (ExecutionException | InterruptedException e) {
                LOGGER.error(e);
                e.printStackTrace();
            }
        }
    }

    private JPanel initSaveVisualsButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton saveImgButton = new JButton("Save to file");
        saveImgButton.addActionListener(e -> {
            String path = PortexSwingUtils.getSaveFileNameFromUser(this);
            new SaveImageFileWorker(path).execute();
        });

        buttonPanel.add(saveImgButton);
        return buttonPanel;
    }

    private class SaveImageFileWorker extends SwingWorker<Boolean, Void> {
        private String path;

        public SaveImageFileWorker(String path) {
            this.path = path;
        }

        @Override
        protected Boolean doInBackground() {
            try {
                if (!path.toLowerCase().endsWith(".png")) {
                    path += ".png";
                }
                ImageIO.write(visPanel.getImage(), "png", new File(path));
            } catch (IOException ex) {
                LOGGER.error(ex);
                ex.printStackTrace();
                return false;
            }
            return true;
        }

        @Override
        protected void done() {
            try {
                Boolean success = get();
                if (success) {
                    JOptionPane.showMessageDialog(PEDetailsPanel.this,
                            "File successfully saved",
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Unable to save file :(",
                            "Something went wrong",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (ExecutionException | InterruptedException e) {
                LOGGER.error(e);
                e.printStackTrace();
            }
        }
    }

    public void setPeData(FullPEData peData) {
        this.peData = peData;
        tabbedPanel.setPeData(peData);
        iconPanel.setPeData(peData);
        signaturesPanel.setPeData(peData);
        try {
            visPanel.visualizePE(peData.getFile());
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error(e);
        }
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.isHexEnabled = hexEnabled;
        tabbedPanel.setHexEnabled(hexEnabled);
        signaturesPanel.setHexEnabled(hexEnabled);
        parent.refreshSelection();
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
        if (header.getCharacteristics().size() == 0) {
            text += "no characteristics set";
        }
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
        List<Object[]> dirEntries = new ArrayList<>();
        for (DataDirEntry de : header.getDataDirectory().values()) {
            Long offset = de.getFileOffset(secTable);
            Long size = de.getDirectorySize();
            Long va = de.getVirtualAddress();
            Optional<SectionHeader> hOpt = de.maybeGetSectionTableEntry(secTable);
            String section = hOpt.isPresent() ? hOpt.get().getNumber() + ". " + hOpt.get().getName() : "NaN";
            Long valueOffset = de.getTableEntryOffset();
            Object[] row = {de.getKey(), va, offset, size, section, valueOffset};
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
        iconPanel.setDropTarget(dt);
        signaturesPanel.setDropTarget(dt);
    }

    private void showDescriptionPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "DESCRIPTION");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to DESCRIPTION");
    }

    private void showTablePanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "TABLE");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to TABLE");
    }

    private void showIconPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "ICONS");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to ICONS");
    }

    private void showTabbedPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "TABBED");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to TABBED");
    }

    private void showSignaturesPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "SIGNATURES");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to SIGNATURES");
    }

    public void showVisualization() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "VISUALIZATION");
        rightPanel.setVisible(false);
        LOGGER.debug("Card panel set to VISUALIZATION");
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
        JTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        for (StandardField field : entries) {
            Object[] row = {field.getDescription(), field.getValue(), field.getOffset()};
            model.addRow(row);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private void showFieldEntriesAndDescription(List<StandardField> entries, String[] tableHeader, String text) {
        cleanUpTablePanel();
        tablePanel.add(new JScrollPane(new JTextArea(text)));
        JTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        for (StandardField field : entries) {
            Object[] row = {field.getDescription(), field.getValue(), field.getOffset()};
            model.addRow(row);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private void showTextEntries(List<Object[]> entries, String[] tableHeader) {
        cleanUpTablePanel();
        JTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        model.setColumnIdentifiers(tableHeader);
        for (Object[] row : entries) {
            model.addRow(row);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private void showTextEntriesAndDescription(List<Object[]> entries, String[] tableHeader, String text) {
        cleanUpTablePanel();
        JTextArea t = new JTextArea(text); // if you end up using this again, create a class instead
        t.setEditable(false);
        t.setDragEnabled(true);
        t.setLineWrap(true);
        tablePanel.add(new JScrollPane(t));
        JTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        model.setColumnIdentifiers(tableHeader);
        for (Object[] row : entries) {
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

    private String toHexIfEnabled(Long num) {
        if (isHexEnabled) {
            return "0x" + Long.toHexString(num);
        }
        return num.toString();
    }

    public void showOverlay() {
        if (peData == null) return;
        try {
            long offset = peData.getOverlay().getOffset();
            long size = peData.getOverlay().getSize();
            double entropy = peData.getOverlayEntropy() * 8;
            List<String> sigs = peData.getOverlaySignatures();

            String text = "Offset: " + toHexIfEnabled(offset) + NL + "Size: " + toHexIfEnabled(size) + NL;
            String packed = entropy >= 7.0 ? " (packed)" : "";
            text += "Entropy: " + String.format("%1.2f", entropy) + packed + NL + NL;
            text += "Signatures: " + NL;
            for (String s : sigs) {
                text += s + NL;
            }
            if (sigs.size() == 0) {
                text += "no matches";
            }

            descriptionField.setText(text);
            showDescriptionPanel();
        } catch (IOException e) {
            String message = "Could not read Overlay! Reason: " + e.getMessage();
            LOGGER.error(message);
            e.printStackTrace();
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
            List<Object[]> entries = richEntries.stream()
                    .map(e -> new Object[]{e.getProductIdStr(), e.getBuildStr(), e.count()})
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

    public void showManifests() {
        if (peData == null) return;
        List<String> manifests = peData.getManifests();
        String MANIFEST_DELIMITER = "\n\n------------------------------------------------------------------------------------------------------------\n\n";
        descriptionField.setText(String.join(MANIFEST_DELIMITER, manifests));
        showDescriptionPanel();
    }

    public void showVersionInfo() {
        if (peData == null) return;
        String[] vsHeader = {"VsInfo key", "Description"};
        showTextEntries(peData.getVersionInfoTable(), vsHeader);
        showTablePanel();
    }

    public void showResources() {
        if (peData == null) return;
        String[] tableHeader = {"Type", "Name", "Language", "Offset", "Size", "Signatures"};
        showTextEntries(peData.getResourceTableEntries(), tableHeader);
        showTablePanel();

    }

    public void showIcons() {
        if (peData == null) return;
        showIconPanel();
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
        String[] tableHeader = {"Description", "Value", "Value offset"};
        List<StandardField> entries = peData.getDebugTableEntries();
        String text = peData.getDebugInfo();
        showFieldEntriesAndDescription(entries, tableHeader, text);
        showTablePanel();
    }

    public void showAnomalies() {
        if (peData == null) return;
        String[] tableHeader = {"Description", "Type", "Subtype", "Field or Structure "};
        List<Object[]> entries = peData.getAnomaliesTable();

        showTextEntries(entries, tableHeader);
        showTablePanel();
    }

    public void showHashes() {
        if (peData == null) return;

        String[] tableHeader = {"Section", "Type", "Hash value"};
        List<Object[]> entries = peData.getSectionHashTableEntries();
        String text = peData.getHashesReport();
        showTextEntriesAndDescription(entries, tableHeader, text);
        showTablePanel();
    }

    public void showSignatures() {
        showSignaturesPanel();
    }

    public void showPEFormat() {
        showEmpty();
    }
}
