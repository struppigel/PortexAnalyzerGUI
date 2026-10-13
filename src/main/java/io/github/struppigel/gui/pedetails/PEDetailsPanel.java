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
package io.github.struppigel.gui.pedetails;

import io.github.struppigel.parser.RichHeader;
import io.github.struppigel.parser.StandardField;
import io.github.struppigel.parser.coffheader.COFFFileHeader;
import io.github.struppigel.parser.msdos.MSDOSHeader;
import io.github.struppigel.parser.optheader.DataDirEntry;
import io.github.struppigel.parser.optheader.DataDirectoryKey;
import io.github.struppigel.parser.optheader.OptionalHeader;
import io.github.struppigel.parser.sections.SectionHeader;
import io.github.struppigel.parser.sections.SectionLoader;
import io.github.struppigel.parser.sections.SectionTable;
import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.MainFrame;
import io.github.struppigel.gui.PEFieldsTable;
import io.github.struppigel.gui.pedetails.signatures.SignaturesPanel;
import io.github.struppigel.gui.utils.InfoCard;
import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.gui.utils.TableContent;
import io.github.struppigel.gui.visualizer.InteractiveVisualizerPanel;
import io.github.struppigel.gui.visualizer.RegionNavigator;
import io.github.struppigel.gui.visualizer.Spacing;
import io.github.struppigel.gui.visualizer.VisualizationModel;
import io.github.struppigel.parser.sections.debug.ExtendedDLLCharacteristics;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.settings.PortexSettingsKey;
import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import com.google.common.base.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static io.github.struppigel.parser.optheader.StandardFieldEntryKey.ADDR_OF_ENTRY_POINT;

/**
 * Displays the data in the middle.
 */
public class PEDetailsPanel extends JPanel {
    private static final Logger LOGGER = LogManager.getLogger();
    private final String NL = System.getProperty("line.separator");
    private final JPanel rightPanel;
    private final MainFrame parent;
    private final PortexSettings settings;
    private FullPEData peData;

    /**
     * Part of table panel
     */
    private final List<JTable> tables = new ArrayList<>();
    private final InfoCard overlayCard = new InfoCard();
    private final InfoCard overviewCard = new InfoCard();
    private final JTextArea descriptionField = new JTextArea();
    private JScrollPane scrollPaneDescription;

    /**
     * Contains the tablePanel, tabbedPanel and descriptionField
     */
    private final JPanel cardPanel = new JPanel(new CardLayout());
    private String visibleCardName;

    /**
     * Contains the tables
     */
    private final JPanel tablePanel = new JPanel();
    private SectionsTabbedPanel sectionsPanel;
    private InteractiveVisualizerPanel visualizerView;

    private IconPanel iconPanel = new IconPanel();
    private final SignaturesPanel signaturesPanel;
    private final AuthenticodePanel authenticodePanel;
    private boolean isHexEnabled = true;
    private FileContentPreviewPanel previewPanel;
    private TabbedPanel tabbedPanel;
    private final VisualizationModel visModel;
    // true if this panel created the model itself and must load the files into it
    private final boolean ownsVisModel;

    /**
     * Creates the panel with its own visualization model, e.g., for tests.
     */
    public PEDetailsPanel(JPanel rightPanel, MainFrame mainFrame, PortexSettings settings, FileContentPreviewPanel previewPanel) {
        this(rightPanel, mainFrame, settings, previewPanel, new VisualizationModel(), true);
    }

    /**
     * @param visModel the visualization model that the owner loads the files into
     */
    public PEDetailsPanel(JPanel rightPanel, MainFrame mainFrame, PortexSettings settings, FileContentPreviewPanel previewPanel,
                          VisualizationModel visModel) {
        this(rightPanel, mainFrame, settings, previewPanel, visModel, false);
    }

    private PEDetailsPanel(JPanel rightPanel, MainFrame mainFrame, PortexSettings settings, FileContentPreviewPanel previewPanel,
                           VisualizationModel visModel, boolean ownsVisModel) {
        super(new GridLayout(1, 0));
        this.rightPanel = rightPanel;
        this.parent = mainFrame;
        this.settings = settings;
        this.previewPanel = previewPanel;
        this.visModel = visModel;
        this.ownsVisModel = ownsVisModel;
        signaturesPanel = new SignaturesPanel(previewPanel);
        authenticodePanel = new AuthenticodePanel(previewPanel);
        initDetails();
        linkVisualizationAndPreview();
    }

    /**
     * @return the bytes per line of the hex view the user picked, 0 for auto
     */
    private int savedHexBytesPerLine() {
        try {
            return Integer.parseInt(settings.get(PortexSettingsKey.HEX_BYTES_PER_LINE));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Every navigation to an offset, e.g., in the tree or a table, goes through the preview. The visualization
     * selects the structure there, and the preview shows the offsets the user clicks in the visualization.
     */
    private void linkVisualizationAndPreview() {
        previewPanel.addOffsetListener((offset, source) -> {
            if (source != visModel) {
                visModel.reveal(offset);
            } else {
                // a signature picked in the visualization; clicks into the tables must not switch the tab
                authenticodePanel.selectSignatureAt(offset);
            }
        });
        visualizerView.setOffsetClickListener(offset -> previewPanel.showContentAtOffset(offset, visModel));
        visModel.addListener(new VisualizationModel.Listener() {
            @Override
            public void selectionChanged() {
                highlightSelectionInPreview();
            }

            @Override
            public void dataChanged() {
                previewPanel.setHighlight(-1, -1);
            }
        });
    }

    private void highlightSelectionInPreview() {
        FileRegion region = visModel.getSelectedRegion();
        if (visModel.hasRangeSelection()) {
            previewPanel.setHighlight(visModel.getRangeStart(), visModel.getRangeEnd());
        } else if (region != null) {
            // the part of the structure at the selected offset, structures may consist of several parts
            long start = region.getStart();
            long end = region.getEnd();
            for (PhysicalLocation loc : region.getLocations()) {
                long offset = visModel.getSelectedOffset();
                if (offset >= loc.from() && offset < loc.from() + loc.size()) {
                    start = loc.from();
                    end = loc.from() + loc.size();
                }
            }
            previewPanel.setHighlight(start, end);
        } else {
            previewPanel.setHighlight(-1, -1);
        }
    }

    private void initDetails() {
        // only description
        descriptionField.setText("Drop file here");
        descriptionField.setEditable(false);
        descriptionField.setDropTarget(this.getDropTarget());
        enableDrag(descriptionField);
        descriptionField.setLineWrap(true);
        descriptionField.setBorder(Spacing.padding());
        for (InfoCard card : new InfoCard[]{overviewCard, overlayCard}) {
            card.setDropTarget(this.getDropTarget());
            enableDrag(card);
        }
        scrollPaneDescription = new JScrollPane(descriptionField);

        // interactive visualization
        visualizerView = new InteractiveVisualizerPanel(visModel);
        visualizerView.setHexBytesPerLine(savedHexBytesPerLine());
        visualizerView.setHexBytesPerLineListener(bytes -> {
            settings.put(PortexSettingsKey.HEX_BYTES_PER_LINE, String.valueOf(bytes));
            try {
                settings.writeSettings();
            } catch (IOException e) {
                LOGGER.error(e);
            }
        });

        // icons page + button
        JPanel iconWrapperPanel = new JPanel();
        JPanel icoButtonPanel = initSaveIconsButtonPanel();
        iconWrapperPanel.setLayout(new BorderLayout());
        iconWrapperPanel.add(new JScrollPane(iconPanel), BorderLayout.CENTER);
        iconWrapperPanel.add(icoButtonPanel, BorderLayout.PAGE_END);

        // description + dump button
        JPanel descriptionAndButtonWrapperPanel = new JPanel();
        JPanel dumpButtonPanel = initDumpButtonPanel();
        descriptionAndButtonWrapperPanel.setLayout(new BorderLayout());
        descriptionAndButtonWrapperPanel.add(new JScrollPane(overlayCard), BorderLayout.CENTER);
        descriptionAndButtonWrapperPanel.add(dumpButtonPanel, BorderLayout.PAGE_END);

        sectionsPanel = new SectionsTabbedPanel();
        tabbedPanel = new TabbedPanel(previewPanel);
        tablePanel.setLayout(new GridLayout(0, 1));

        cardPanel.add(tablePanel, "TABLE");
        cardPanel.add(scrollPaneDescription, "DESCRIPTION");
        cardPanel.add(descriptionAndButtonWrapperPanel, "DESCBUTTON");
        cardPanel.add(tabbedPanel, "TABBED");
        cardPanel.add(sectionsPanel, "SECTIONS");
        cardPanel.add(visualizerView, "VISUALIZATION");
        cardPanel.add(iconWrapperPanel, "ICONS");
        cardPanel.add(signaturesPanel, "SIGNATURES");
        cardPanel.add(authenticodePanel, "AUTHENTICODE");

        //add the table to the frame
        setLayout(new BorderLayout());
        add(cardPanel, BorderLayout.CENTER);

        showDescriptionPanel();
    }

    // drag support needs a display, which is not available in headless mode, e.g., in tests
    private static void enableDrag(JTextComponent textComponent) {
        if (!GraphicsEnvironment.isHeadless()) {
            textComponent.setDragEnabled(true);
        }
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

    /**
     * Writes the bytes of the input file from offset 0 until the end offset (exclusive) to the output file.
     */
    static void writeUntil(File in, long endOffset, File outFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(in, "r");
             FileOutputStream out = new FileOutputStream(outFile)) {
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
                totalBytesRead += bytesRead;
            }
        }
    }

    /**
     * Writes the bytes of the input file from the start offset until the end of the file to the output file.
     */
    static void writeFrom(File in, long startOffset, File outFile) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(in, "r");
             FileOutputStream out = new FileOutputStream(outFile)) {
            raf.seek(startOffset);
            byte[] buffer = new byte[2048];
            int bytesRead;
            while ((bytesRead = raf.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
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
                try {
                    writeUntil(peData.getFile(), peData.getOverlay().getOffset(), file);
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
                try {
                    writeFrom(peData.getFile(), peData.getOverlay().getOffset(), file);
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

    public void setPeData(FullPEData peData) {
        this.peData = peData;
        if (ownsVisModel) {
            visModel.load(peData);
        }
        sectionsPanel.setPeData(peData);
        iconPanel.setPeData(peData);
        signaturesPanel.setPeData(peData);
    }

    /**
     * Zooms the visualization in to the offset and selects the structure there. Does not switch to the
     * visualization, select its tree node for that.
     */
    public void zoomVisualizationTo(long offset) {
        visualizerView.zoomToOffset(offset);
    }

    /**
     * @param navigator shows structures that the user picks in the visualization in the tree
     */
    public void setRegionNavigator(RegionNavigator navigator) {
        visualizerView.setRegionNavigator(navigator);
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.isHexEnabled = hexEnabled;
        previewPanel.setHexEnabled(hexEnabled);
        sectionsPanel.setHexEnabled(hexEnabled);
        signaturesPanel.setHexEnabled(hexEnabled);
        tabbedPanel.setHexEnabled(hexEnabled);
        visualizerView.setHexEnabled(hexEnabled);
        if (parent != null) {
            parent.refreshSelection();
        }
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
            showDescriptionText("");
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
        overlayCard.setDropTarget(dt);
        overviewCard.setDropTarget(dt);
        tablePanel.setDropTarget(dt);
        sectionsPanel.setDropTarget(dt);
        iconPanel.setDropTarget(dt);
        signaturesPanel.setDropTarget(dt);
        authenticodePanel.setDropTarget(dt);
    }

    private void showCard(String name, boolean rightPanelVisible) {
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, name);
        rightPanel.setVisible(rightPanelVisible);
        visibleCardName = name;
        LOGGER.debug("Card panel set to " + name);
    }

    /**
     * @return the name of the card that is currently shown, e.g., TABLE or DESCRIPTION
     */
    String getVisibleCardName() {
        return visibleCardName;
    }

    VisualizationModel getVisualizationModel() {
        return visModel;
    }

    private void showDescriptionPanel() {
        showCard("DESCRIPTION", true);
    }

    /**
     * Shows plain text on the description page.
     */
    private void showDescriptionText(String text) {
        descriptionField.setText(text);
        descriptionField.setCaretPosition(0);
        scrollPaneDescription.setViewportView(descriptionField);
        showDescriptionPanel();
    }

    /**
     * @return a card with the lines of the text as rows, bullets and headings
     */
    private InfoCard descriptionCard(String text) {
        InfoCard card = new InfoCard();
        enableDrag(card);
        return card.addText(text).render();
    }

    private void showDescriptionButtonPanel() {
        showCard("DESCBUTTON", true);
    }

    private void showTablePanel() {
        showCard("TABLE", true);
    }

    private void showSectionPanel() {
        showCard("SECTIONS", true);
    }

    private void showIconPanel() {
        showCard("ICONS", true);
    }

    private void showTabbedPanel() {
        showCard("TABBED", true);
    }

    private void showSignaturesPanel() {
        showCard("SIGNATURES", true);
    }

    public void showVisualization() {
        showCard("VISUALIZATION", false);
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
        tablePanel.add(new JScrollPane(descriptionCard(text)));
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
        tablePanel.add(new JScrollPane(descriptionCard(text)));
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
        showDescriptionText("");
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
            List<SignatureMatch> sigs = peData.getOverlaySignatures();

            overlayCard.clear().heading("Overlay");
            overlayCard.row("Offset", toHexIfEnabled(offset)).row("Size", toHexIfEnabled(size));
            if (entropy >= 7.0) {
                overlayCard.rowWithBadge("Entropy", String.format("%1.2f", entropy), "packed", InfoCard.Kind.WARN);
            } else {
                overlayCard.row("Entropy", String.format("%1.2f", entropy));
            }
            overlayCard.heading("Signatures");
            for (SignatureMatch s : sigs) {
                overlayCard.bullet(s.getName() + " at " + toHexIfEnabled(s.getOffset()) + " (" + s.getLength() + " bytes)");
            }
            if (sigs.size() == 0) {
                overlayCard.paragraph("no matches");
            }
            overlayCard.render();
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
        showDescriptionText(String.join(MANIFEST_DELIMITER, manifests));

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

    public void showBoundImports() {
        if (peData == null) return;
        String[] tableHeader = {"Name", "Offset Module Name", "Time Date Stamp", "Forwarder Refs", "Raw Offset"};
        showTextEntries(peData.getBoundImportEntries(), tableHeader, 4);
        showTablePanel();
        SectionTable sectionTable = peData.getPeData().getSectionTable();
        Long offset = peData.getPeData()
                .getOptionalHeader()
                .getDataDirectory()
                .get(DataDirectoryKey.BOUND_IMPORT)
                .getFileOffset(sectionTable);
        previewPanel.showContentAtOffset(offset);
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
        table.setWrappedColumn(0);
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

    public void showAuthenticode() {
        if (peData == null) return;
        authenticodePanel.show(peData, isHexEnabled);
        showCard("AUTHENTICODE", true);
        Long offset = getFileOffsetForDataDirectoryKeyOrZero(DataDirectoryKey.CERTIFICATE_TABLE);
        previewPanel.showContentAtOffset(offset);
    }

    /**
     * @return the panel of the Authenticode node, for tests
     */
    AuthenticodePanel getAuthenticodePanel() {
        return authenticodePanel;
    }

    public void showPEFormat() {
        if (peData == null) return;
        overviewCard.clear().heading("Overview");
        overviewCard.tiles(3,
                "Anomalies", String.valueOf(peData.getAnomaliesTable().size()),
                "Sections", String.valueOf(peData.getPeData().getSectionTable().getNumberOfSections()),
                "Exported symbols", String.valueOf(peData.getExportTableEntries().size()),
                "Import entries", String.valueOf(peData.getImportTableEntries().size()),
                "Resources", String.valueOf(peData.getResources().size()),
                "Debug entries", String.valueOf(peData.getDebugTableEntries().size()));
        overviewCard.badgeRow("Has overlay", peData.overlayExists() ? "Yes" : "No", InfoCard.Kind.NEUTRAL);
        String signature = AuthenticodeTables.statusLine(peData.getCertificateTable(), peData.getAuthenticodeVerification());
        String status = signature.substring(signature.indexOf(": ") + 2);
        overviewCard.badgeRow("Authenticode", status, AuthenticodeTables.kindOf(status));
        overviewCard.heading("Reversing hints");
        boolean hints = false;
        for (String line : peData.getReHintsReport().split("\r?\n")) {
            String hint = line.trim();
            if (hint.isEmpty() || hint.startsWith("Scanned File:") || InfoCard.isUnderline(hint)
                    || hint.equals("Reverse Engineering Hints")) {
                continue; // the title and its underline make the text report of the CLI look nice
            }
            hints = true;
            if (hint.startsWith("Total hints: ")) {
                overviewCard.row("Total hints", hint.substring("Total hints: ".length()));
            } else if (hint.startsWith("* ")) {
                overviewCard.textBullet(hint.substring(2).trim());
            } else {
                overviewCard.lead(hint);
            }
        }
        if (!hints) {
            overviewCard.paragraph("no hints");
        }
        overviewCard.render();
        scrollPaneDescription.setViewportView(overviewCard);
        showDescriptionPanel();
        previewPanel.showContentAtOffset(0L);
    }
}
