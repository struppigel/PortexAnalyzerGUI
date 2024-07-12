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

import com.github.struppigel.parser.RichHeader;
import com.github.struppigel.parser.StandardField;
import com.github.struppigel.parser.coffheader.COFFFileHeader;
import com.github.struppigel.parser.msdos.MSDOSHeader;
import com.github.struppigel.parser.optheader.DataDirEntry;
import com.github.struppigel.parser.optheader.DataDirectoryKey;
import com.github.struppigel.parser.optheader.OptionalHeader;
import com.github.struppigel.parser.sections.SectionHeader;
import com.github.struppigel.parser.sections.SectionLoader;
import com.github.struppigel.parser.sections.SectionTable;
import com.github.struppigel.gui.FullPEData;
import com.github.struppigel.gui.MainFrame;
import com.github.struppigel.gui.PEFieldsTable;
import com.github.struppigel.gui.VisualizerPanel;
import com.github.struppigel.gui.pedetails.signatures.SignaturesPanel;
import com.github.struppigel.gui.utils.PortexSwingUtils;
import com.github.struppigel.gui.utils.TableContent;
import com.github.struppigel.parser.sections.debug.ExtendedDLLCharacteristics;
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
import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.github.struppigel.parser.optheader.StandardFieldEntryKey.ADDR_OF_ENTRY_POINT;

/**
 * Displays the data in the middle.
 */
public class PEDetailsPanel extends JPanel {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String DEFAULT_SAVE_FILENAME = "portex_visualization.png";
    private final String NL = System.getProperty("line.separator");
    private final JPanel rightPanel;
    private final MainFrame parent;
    private final PortexSettings settings;
    private FullPEData peData;

    /**
     * Part of table panel
     */
    private final List<JTable> tables = new ArrayList<>();
    private final JTextArea descriptionWithButtonArea = new JTextArea();
    private final JTextArea descriptionField = new JTextArea();

    /**
     * Contains the tablePanel, tabbedPanel and descriptionField
     */
    private final JPanel cardPanel = new JPanel(new CardLayout());

    /**
     * Contains the tables
     */
    private final JPanel tablePanel = new JPanel();
    private SectionsTabbedPanel sectionsPanel;
    private VisualizerPanel visPanel = new VisualizerPanel(true, true, true, 180);

    private IconPanel iconPanel = new IconPanel();
    private final SignaturesPanel signaturesPanel;
    private boolean isHexEnabled = true;
    private FileContentPreviewPanel previewPanel;
    private TabbedPanel tabbedPanel;

    public PEDetailsPanel(JPanel rightPanel, MainFrame mainFrame, PortexSettings settings, FileContentPreviewPanel previewPanel) {
        super(new GridLayout(1, 0));
        this.rightPanel = rightPanel;
        this.parent = mainFrame;
        this.settings = settings;
        this.previewPanel = previewPanel;
        signaturesPanel = new SignaturesPanel(settings, previewPanel);
        initDetails();
    }

    private void initDetails() {
        // only description
        descriptionField.setText("Drop file here");
        descriptionField.setEditable(false);
        descriptionField.setDropTarget(this.getDropTarget());
        descriptionField.setDragEnabled(true);
        descriptionField.setLineWrap(true);
        JScrollPane scrollPaneDescription = new JScrollPane(descriptionField);

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

        // description + dump button
        descriptionWithButtonArea.setText("");
        descriptionWithButtonArea.setEditable(false);
        descriptionWithButtonArea.setDragEnabled(true);
        descriptionWithButtonArea.setDropTarget(this.getDropTarget());
        descriptionWithButtonArea.setLineWrap(true);
        JPanel descriptionAndButtonWrapperPanel = new JPanel();
        JPanel dumpButtonPanel = initDumpButtonPanel();
        descriptionAndButtonWrapperPanel.setLayout(new BorderLayout());
        descriptionAndButtonWrapperPanel.add(new JScrollPane(descriptionWithButtonArea), BorderLayout.CENTER);
        descriptionAndButtonWrapperPanel.add(dumpButtonPanel, BorderLayout.PAGE_END);

        sectionsPanel = new SectionsTabbedPanel();
        tabbedPanel = new TabbedPanel(previewPanel);
        tablePanel.setLayout(new GridLayout(0, 1));

        cardPanel.add(tablePanel, "TABLE");
        cardPanel.add(scrollPaneDescription, "DESCRIPTION");
        cardPanel.add(descriptionAndButtonWrapperPanel, "DESCBUTTON");
        cardPanel.add(tabbedPanel, "TABBED");
        cardPanel.add(sectionsPanel, "SECTIONS");
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

    private JPanel initDumpButtonPanel() {
        JPanel buttonPanel = new JPanel();
        JButton saveButton = new JButton("Dump overlay");
        saveButton.addActionListener(e -> {
            String defaultFileName = peData.getFile().getAbsolutePath() + ".overlay";
            String path = PortexSwingUtils.getSaveFileNameFromUser(this, defaultFileName);
            if(PortexSwingUtils.checkIfFileExistsAndAskIfOverwrite(this, new File(path))) {
                new DumpOverlayWorker(path).execute();
            }
        });
        JButton removeButton = new JButton("Remove overlay");
        removeButton.addActionListener(e -> {
            String defaultFileName = peData.getFile().getAbsolutePath() + ".truncated";
            String path = PortexSwingUtils.getSaveFileNameFromUser(this, defaultFileName);
            if(PortexSwingUtils.checkIfFileExistsAndAskIfOverwrite(this, new File(path))) {
                new RemoveOverlayWorker(path).execute();
            }
        });
        buttonPanel.add(saveButton);
        buttonPanel.add(removeButton);
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

    private class RemoveOverlayWorker extends SwingWorker<String, Void> {

        private final String outFile;

        public RemoveOverlayWorker(String outFile) {
            this.outFile = outFile;
        }

        @Override
        protected String doInBackground() {
            String resultMessage = "something unknown went wrong";
            File file = new File(outFile);
            if(!file.isDirectory()) {
                try (RandomAccessFile raf = new RandomAccessFile(peData.getFile(), "r");
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    long endOffset = peData.getOverlay().getOffset();
                    raf.seek(0);
                    byte[] buffer = new byte[2048];
                    int bytesRead;
                    long totalBytesRead = 0;
                    while ((bytesRead = raf.read(buffer)) != -1) {
                        // end was reached, we calculate the rest of the buffer
                        if (totalBytesRead + bytesRead > endOffset) {
                            int bytesToWrite = (int) (endOffset - totalBytesRead);
                            out.write(buffer, 0, bytesToWrite);
                            break;
                        }
                        out.write(buffer, 0, bytesRead);
                    }
                    resultMessage = "success";
                } catch (IOException e) {
                    e.printStackTrace();
                    resultMessage = e.getMessage();
                }
            } else {
                resultMessage = "given output file is a directory";
            }
            return resultMessage;
        }

        @Override
        protected void done() {
            try {
                String msg = get();
                if (msg.equals("success")) {
                    JOptionPane.showMessageDialog(null,
                            "File successfully saved to " + outFile,
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Unable to save file :(. " + msg,
                            "Something went wrong",
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (ExecutionException | InterruptedException e) {
                LOGGER.error(e);
                e.printStackTrace();
            }
        }
    }

    private class DumpOverlayWorker extends SwingWorker<String, Void> {

        private final String outFile;

        public DumpOverlayWorker(String outFile) {
            this.outFile = outFile;
        }

        @Override
        protected String doInBackground() {
            String resultMessage = "something unknown went wrong";
            File file = new File(outFile);
            if(!file.isDirectory()) {
                try (RandomAccessFile raf = new RandomAccessFile(peData.getFile(), "r");
                     FileOutputStream out = new FileOutputStream(outFile)) {
                    long offset = peData.getOverlay().getOffset();
                    raf.seek(offset);
                    byte[] buffer = new byte[2048];
                    int bytesRead;
                    while ((bytesRead = raf.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    resultMessage = "success";
                } catch (IOException e) {
                    e.printStackTrace();
                    resultMessage = e.getMessage();
                }

            } else {
                resultMessage = "given output file is a directory";
            }
            return resultMessage;
        }

        @Override
        protected void done() {
            try {
                String msg = get();
                if (msg.equals("success")) {
                    JOptionPane.showMessageDialog(null,
                            "File successfully saved to " + outFile,
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Unable to save file :(. " + msg,
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
            String path = PortexSwingUtils.getSaveFileNameFromUser(this, DEFAULT_SAVE_FILENAME);
            if(path != null) {

                if (!path.toLowerCase().endsWith(".png")) {
                    path += ".png";
                }
                Boolean canWrite = PortexSwingUtils.checkIfFileExistsAndAskIfOverwrite(this, new File(path));
                if(canWrite){
                    new SaveImageFileWorker(path).execute();
                }
            }
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
                            "File successfully saved under " + path,
                            "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,
                            "Unable to save file " + path,
                            "Something went wrong :(",
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
        sectionsPanel.setPeData(peData);
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
        previewPanel.setHexEnabled(hexEnabled);
        sectionsPanel.setHexEnabled(hexEnabled);
        signaturesPanel.setHexEnabled(hexEnabled);
        tabbedPanel.setHexEnabled(hexEnabled);
        parent.refreshSelection();
    }

    public void showDosStub() {
        if (peData != null) {
            MSDOSHeader header = peData.getPeData().getMSDOSHeader();
            List<StandardField> entries = header.getHeaderEntries();
            showFieldEntries(entries);
            LOGGER.debug("MS DOS Stub shown");
            showTablePanel();
            previewPanel.showContentAtOffset(0);
        } else {
            LOGGER.warn("PE Data is null!");
        }
    }

    public void showCoffFileHeader() {
        if (peData == null) return;
        COFFFileHeader header = peData.getPeData().getCOFFFileHeader();
        List<StandardField> entries = header.getHeaderEntries();
        String date = peData.getPeData().isReproBuild() ? "invalid - reproducibility build" : header.getTimeDate().toString();
        String text = "Time date stamp : " + date + NL;
        text += "Machine type: " + header.getMachineType().getDescription() + NL;
        text += "Characteristics: " + NL;
        text += header.getCharacteristics().stream().map(ch -> "\t* " + ch.getDescription()).collect(Collectors.joining(NL));
        if (header.getCharacteristics().size() == 0) {
            text += "no characteristics set";
        }
        String[] tableHeader = {"Description", "Value", "File offset"};
        showFieldEntriesAndDescription(entries, tableHeader, text, 2);
        LOGGER.debug("COFF File header shown");
        showTablePanel();
        previewPanel.showContentAtOffset(header.getOffset());
    }

    public void showOptionalHeader() {
        showEmpty();
        previewPanel.showContentAtOffset(peData.getPeData().getOptionalHeader().getOffset());
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
        String[] tableHeader = {"Description", "Value", "File offset"};
        String text = "Linker version: " + header.getLinkerVersionDescription() + NL;
        text += "Magic Number: " + header.getMagicNumber().getDescription() + NL;
        showFieldEntriesAndDescription(entries, tableHeader, text, 2);
        LOGGER.debug("Standard Fields shown");
        showTablePanel();
        previewPanel.showContentAtOffset(header.getOffset());
    }

    public void showRTStrings() {
        if (peData == null) return;
        List<Object[]> entries = peData.getRTStringTableEntries();
        String[] tableHeader = {"ID", "String"};
        showTextEntries(entries, tableHeader, -1); //TODO add offsets for RT_TABLE entries
        LOGGER.debug("RT_STRINGS shown");

        showTablePanel();
        // find offset for first RT_STRING table
        Long offset = getMinOffsetForResourceTypeOrZero("RT_STRING");
        previewPanel.showContentAtOffset(offset);
    }

    public void showDotNetMetadataRoot() {
        if (peData == null) return;
        List<StandardField> entries = peData.getDotNetMetadataRootEntries();
        String[] tableHeader = {"Key", "Value", "Offset"};
        int offsetColumn = 2;
        long startOffset = peData.getDotNetMetadataRootOffset();
        String description = "MetadataRoot version string: " + peData.getDotNetMetadataVersionString();
        showFieldEntriesAndDescription(entries, tableHeader, description, offsetColumn);
        LOGGER.debug(".NET MetadataRoot shown");
        showTablePanel();
        previewPanel.showContentAtOffset(startOffset);
    }

    public void showDotNetStreamHeaders() {
        if (peData == null) return;
        List<Object[]> entries = peData.getDotNetStreamHeaderEntries();
        String[] tableHeader = {"Stream name", "Size", "BSJB offset", "File offset"};
        showTextEntries(entries, tableHeader, 3);
        LOGGER.debug(".NET Stream Headers shown");
        showTablePanel();
        previewPanel.showContentAtOffset(peData.getDotNetMetadataRootOffset());
    }

    public void showOptimizedStream() {
        if (peData == null) return;
        List<StandardField> entries = peData.getOptimizedStreamEntries();
        showFieldEntries(entries);
        LOGGER.debug("#~ shown");
        showTablePanel();
        Long offset = Collections.min(
                peData.getOptimizedStreamEntries()
                        .stream()
                        .map(e -> e.getOffset())
                        .collect(Collectors.toList()));
        previewPanel.showContentAtOffset(offset);
    }

    public void showWindowsFieldsTable() {
        if (peData == null) return;
        OptionalHeader header = peData.getPeData().getOptionalHeader();
        List<StandardField> entries = new ArrayList<>(header.getWindowsSpecificFields().values());
        String[] tableHeader = {"Description", "Value", "File offset"};

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
        java.util.Optional<ExtendedDLLCharacteristics> exDll = peData.getPeData().loadExtendedDllCharacteristics();
        if(exDll.isPresent() && (exDll.get().getCETCompat() || exDll.get().getForwardCFICompat())) {
            text += NL + "Extended DLL Characteristics:" + NL;
            if ( exDll.get().getCETCompat() ) {
                text += "\t*  CET Compat" + NL;
            }
            if ( exDll.get().getForwardCFICompat() ) {
                text += "\t*  Foward CFI Compat" + NL;
            }
        }
        showFieldEntriesAndDescription(entries, tableHeader, text, 2);
        LOGGER.debug("Windows Fields shown");
        showTablePanel();
        previewPanel.showContentAtOffset(header.getOffset());
    }

    public void showDataDirectoryTable() {
        if (peData == null) return;
        OptionalHeader header = peData.getPeData().getOptionalHeader();
        SectionTable secTable = peData.getPeData().getSectionTable();
        List<Object[]> dirEntries = new ArrayList<>();
        long startOffset = 0L;
        for (DataDirEntry de : header.getDataDirectory().values()) {
            Long offset = de.getFileOffset(secTable);
            if(startOffset == 0 || offset < startOffset) {
                startOffset = offset;
            }
            Long size = de.getDirectorySize();
            Long va = de.getVirtualAddress();
            Optional<SectionHeader> hOpt = de.maybeGetSectionTableEntry(secTable);
            String section = hOpt.isPresent() ? hOpt.get().getNumber() + ". " + hOpt.get().getName() : "NaN";
            Long valueOffset = de.getTableEntryOffset();
            Object[] row = {de.getKey(), va, offset, size, section, valueOffset};
            dirEntries.add(row);
        }
        String[] tableHeader = {"Data directory", "RVA", "-> Offset", "Size", "In section", "Value offset"};
        showTextEntries(dirEntries, tableHeader, 2);
        LOGGER.debug("Windows Fields shown");
        showTablePanel();
        previewPanel.showContentAtOffset(startOffset);
    }

    @Override
    public synchronized void setDropTarget(DropTarget dt) {
        descriptionWithButtonArea.setDropTarget(dt);
        tablePanel.setDropTarget(dt);
        sectionsPanel.setDropTarget(dt);
        iconPanel.setDropTarget(dt);
        signaturesPanel.setDropTarget(dt);
    }

    private void showDescriptionPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "DESCRIPTION");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to DESCRIPTION");
    }

    private void showDescriptionButtonPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "DESCBUTTON");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to DESCBUTTON");
    }

    private void showTablePanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "TABLE");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to TABLE");
    }

    private void showSectionPanel() {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "SECTIONS");
        rightPanel.setVisible(true);
        LOGGER.debug("Card panel set to SECTION");
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
        showSectionPanel();
        Long offset = peData.getPeData().getSectionTable().getOffset();
        previewPanel.showContentAtOffset(offset);
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

    private void showFieldEntries(List<StandardField> entries) {
        String[] tableHeader = {"Description", "Value", "File offset"};
        cleanUpTablePanel();
        PEFieldsTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        for (StandardField field : entries) {
            Object[] row = {field.getDescription(), field.getValue(), field.getOffset()};
            model.addRow(row);
        }

        table.setPreviewOffsetColumn(2, previewPanel);
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private void showFieldEntriesAndDescription(List<StandardField> entries, String[] tableHeader, String text, int offsetColumn) {
        cleanUpTablePanel();
        tablePanel.add(new JScrollPane(new JTextArea(text)));
        PEFieldsTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        for (StandardField field : entries) {
            Object[] row = {field.getDescription(), field.getValue(), field.getOffset()};
            model.addRow(row);
        }
        if(offsetColumn >= 0) {
            table.setPreviewOffsetColumn(offsetColumn, previewPanel);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
    }

    private PEFieldsTable showTextEntries(List<Object[]> entries, String[] tableHeader, int offsetColumn) {
        cleanUpTablePanel();
        PEFieldsTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        model.setColumnIdentifiers(tableHeader);
        for (Object[] row : entries) {
            model.addRow(row);
        }
        if(offsetColumn >= 0) {
            table.setPreviewOffsetColumn(offsetColumn, previewPanel);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
        return table;
    }

    private PEFieldsTable showTextEntriesAndDescription(List<Object[]> entries, String[] tableHeader, String text, int offsetColumn) {
        cleanUpTablePanel();
        JTextArea t = new JTextArea(text); // if you end up using this again, create a class instead
        t.setEditable(false);
        t.setDragEnabled(true);
        t.setLineWrap(true);
        tablePanel.add(new JScrollPane(t));
        PEFieldsTable table = new PEFieldsTable(isHexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(tableHeader);
        model.setColumnIdentifiers(tableHeader);
        for (Object[] row : entries) {
            model.addRow(row);
        }
        if(offsetColumn >= 0) {
            table.setPreviewOffsetColumn(offsetColumn, previewPanel);
        }
        table.setModel(model);
        addTable(table);
        showUpdatesInTablePanel();
        return table;
    }

    public void showPEHeaders() {
        descriptionField.setText("");
        showDescriptionPanel();
        previewPanel.showContentAtOffset(peData.getPeData().getPESignature().getOffset());
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

            descriptionWithButtonArea.setText(text);
            showDescriptionButtonPanel();
            previewPanel.showContentAtOffset(offset);
        } catch (IOException e) {
            String message = "Could not read Overlay! Reason: " + e.getMessage();
            LOGGER.error(message);
            e.printStackTrace();
        }
    }


    public void showClrTable(String name) {
        if (peData == null || !peData.isDotNet()) return;
        if(!peData.getClrTables().containsKey(name) || !peData.getClrTableHeaders().containsKey(name)) return;
        List<Object[]> entries = peData.getClrTables().get(name).stream().map(t -> t.toArray()).collect(Collectors.toList());
        List<String> headers = peData.getClrTableHeaders().get(name);
        String[] headersArray = headers.toArray(new String[headers.size()]);
        int offsetColumn = headersArray.length - 1;
        showTextEntries(entries, headersArray, offsetColumn);
        showTablePanel();
        long tableOffset = peData.getClrTableOffset(name);
        previewPanel.showContentAtOffset(tableOffset);
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
            showTextEntriesAndDescription(entries, tableHeader, text, -1);
            showTablePanel();
            previewPanel.showContentAtOffset(rich.getRichOffset());
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

        // find offset for first RT_MANIFEST table
        Long offset = getMinOffsetForResourceTypeOrZero("RT_MANIFEST");
        previewPanel.showContentAtOffset(offset);
    }

    private Long getMinOffsetForResourceTypeOrZero(String resourceType){
        Long offset = peData.getResources().stream()
                .filter(r -> r.getType().equals(resourceType))
                .mapToLong(r -> r.rawBytesLocation().from())
                .min()
                .orElse(0L);
        return offset;
    }

    public void showVersionInfo() {
        if (peData == null) return;
        String[] vsHeader = {"VsInfo key", "Description"};
        showTextEntries(peData.getVersionInfoTable(), vsHeader, -1);
        showTablePanel();
        Long offset = getMinOffsetForResourceTypeOrZero("RT_VERSION");
        previewPanel.showContentAtOffset(offset);
    }

    public void showResources() {
        if (peData == null) return;
        String[] tableHeader = {"Type", "Name", "Language", "Res. Offset", "Size", "Signatures"};
        showTextEntries(peData.getResourceTableEntries(), tableHeader, 3);
        showTablePanel();
        Long offset = getFileOffsetForDataDirectoryKeyOrZero(DataDirectoryKey.RESOURCE_TABLE);
        previewPanel.showContentAtOffset(offset);

    }

    public void showIcons() {
        if (peData == null) return;
        showIconPanel();
        Long offset = getMinOffsetForResourceTypeOrZero("RT_ICON");
        previewPanel.showContentAtOffset(offset);
    }

    private void showImportsForEntries(List<Object[]> entries, DataDirectoryKey dataDir) {
        if (peData == null) return;
        // Make beautiful with tabs
        String[] tableHeader = {"DLL", "Category", "Name", "Description", "RVA", "Hint"};
        PEFieldsTable table = showTextEntries(entries, tableHeader, -1);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setMaxWidth(200);
        table.getColumnModel().getColumn(5).setMaxWidth(200);
        showTablePanel();
        Long offset = getFileOffsetForDataDirectoryKeyOrZero(dataDir);
        previewPanel.showContentAtOffset(offset);
    }

    public void showDelayLoadImports() {
        showImportsForEntries(peData.getDelayLoadEntries(), DataDirectoryKey.DELAY_IMPORT_DESCRIPTOR);
    }

    public void showImports() {
        showImportsForEntries(peData.getImportTableEntries(), DataDirectoryKey.IMPORT_TABLE);
    }

    private Long getFileOffsetForDataDirectoryKeyOrZero(DataDirectoryKey dataDirKey) {
        Map<DataDirectoryKey, DataDirEntry> dataDirectory = peData.getPeData()
                .getOptionalHeader()
                .getDataDirectory();
        if(dataDirectory.containsKey(dataDirKey)) {
            SectionTable sectionTable = peData.getPeData().getSectionTable();
            return dataDirectory.get(dataDirKey)
                    .getFileOffset( sectionTable );
        }
        return 0L;
    }

    public void showExports() {
        if (peData == null) return;
        // Make beautiful with tabs
        String[] tableHeader = {"Name", "Ordinal", "Symbol RVA", "Forwarder"};
        showTextEntries(peData.getExportTableEntries(), tableHeader, -1);
        showTablePanel();
        //Long offset = peData.getPeData().get
        Long offset = getFileOffsetForDataDirectoryKeyOrZero(DataDirectoryKey.EXPORT_TABLE);
        previewPanel.showContentAtOffset(offset);
    }

    public void showDebugInfo() {
        if (peData == null) return;
        String[] tableHeader = {"Description", "Value", "File offset"};
        List<TableContent> entries = peData.getDebugTableEntries();

        tabbedPanel.setContent(entries, tableHeader);
        tabbedPanel.repaint();
        showTabbedPanel();

        Long offset = getFileOffsetForDataDirectoryKeyOrZero(DataDirectoryKey.DEBUG);
        previewPanel.showContentAtOffset(offset);
    }

    public void showAnomalies() {
        if (peData == null) return;
        String[] tableHeader = {"Description", "Type", "Subtype", "Field or Structure "};
        List<Object[]> entries = peData.getAnomaliesTable();

        PEFieldsTable table = showTextEntries(entries, tableHeader, -1);
        table.getColumnModel().getColumn(0).setPreferredWidth(500);
        showTablePanel();
    }

    public void showHashes() {
        if (peData == null) return;
        String[] tableHeader = {"Section", "Type", "Hash value"};
        List<Object[]> entries = peData.getSectionHashTableEntries();
        String text = peData.getHashesReport();
        PEFieldsTable table = showTextEntriesAndDescription(entries, tableHeader, text, -1);
        table.getColumnModel().getColumn(2).setPreferredWidth(450);
        showTablePanel();
        Long offset = peData.getPeData().getSectionTable().getOffset();
        previewPanel.showContentAtOffset(offset);
    }

    public void showSignatures() {
        showSignaturesPanel();
    }

    public void showPEFormat() {
        if (peData == null) return;
        String summary = "";
        summary += "Anomalies found: " + peData.getAnomaliesTable().size() + NL;
        summary += "Sections: " + peData.getPeData().getSectionTable().getNumberOfSections() + NL;
        summary += "Exported symbols: " + peData.getExportTableEntries().size() + NL;
        summary += "Import entries: " + peData.getImportTableEntries().size() + NL;
        summary += "Resources found: " + peData.getResources().size() + NL;
        summary += "Debug entries loaded: " + peData.getDebugTableEntries().size() + NL;
        summary += "Has overlay: " + (peData.overlayExists() ? "Yes" : "No") + NL;
        summary += NL + peData.getReHintsReport() + NL;
        descriptionField.setText(summary);
        showDescriptionPanel();
        previewPanel.showContentAtOffset(0L);
    }
}
