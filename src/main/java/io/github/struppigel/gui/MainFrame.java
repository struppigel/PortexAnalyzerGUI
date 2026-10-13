/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package io.github.struppigel.gui;

import io.github.struppigel.gui.pedetails.FileContentPreviewPanel;
import io.github.struppigel.gui.pedetails.PEDetailsPanel;
import io.github.struppigel.gui.utils.AppIcon;
import io.github.struppigel.gui.utils.PELoadWorker;
import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.gui.utils.ThemeIcon;
import io.github.struppigel.gui.utils.WorkerKiller;
import io.github.struppigel.gui.utils.WriteSettingsWorker;
import io.github.struppigel.gui.visualizer.CompactVisualizerPanel;
import io.github.struppigel.gui.visualizer.VisualizationModel;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.settings.LookAndFeelSetting;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.settings.PortexSettingsKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ItemEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;

import static javax.swing.SwingUtilities.invokeLater;
import static javax.swing.SwingWorker.StateValue.DONE;

/**
 * The main frame that sets everything in place and holds the main menu.
 */
public class MainFrame extends JFrame {
    private static final Logger LOGGER = LogManager.getLogger();
    private final JLabel filePathLabel = new JLabel();
    private final VisualizationModel visModel = new VisualizationModel();
    private final CompactVisualizerPanel visualizerPanel = new CompactVisualizerPanel(visModel);
    private final PEDetailsPanel peDetailsPanel;
    private final PortexSettings settings;
    // the theme the GUI started with, a changed theme setting applies after a restart
    private final boolean darkTheme;
    private FullPEData pedata = null;
    private PEComponentTree peComponentTree;
    ;
    private JFrame progressBarFrame;
    private JProgressBar progressBar;
    private final static String versionURL = "https://github.com/struppigel/PortexAnalyzerGUI/raw/main/resources/upd_version.txt";
    private final static String currVersion = "/upd_version.txt";
    private final static String releasePage = "https://github.com/struppigel/PortexAnalyzerGUI/releases";
    private final JLabel progressText = new JLabel("Loading ...");
    private final JPanel cardPanel = new JPanel(new CardLayout()) {
        @Override
        public Dimension getPreferredSize() {
            // only the visible card counts, so that the narrow visualizer is not as wide as the hex preview
            for (Component card : getComponents()) {
                if (card.isVisible()) {
                    Dimension size = card.getPreferredSize();
                    Insets insets = getInsets();
                    return new Dimension(size.width + insets.left + insets.right,
                            size.height + insets.top + insets.bottom);
                }
            }
            return super.getPreferredSize();
        }
    };
    private FileContentPreviewPanel fileContent = new FileContentPreviewPanel();

    public MainFrame(PortexSettings settings) {
        super("Portex Analyzer v. " + AboutFrame.version);
        AppIcon.applyTo(this);

        this.settings = settings;
        this.darkTheme = ThemeIcon.isDarkTheme(settings);
        peDetailsPanel = new PEDetailsPanel(cardPanel, this, settings, fileContent, visModel);
        peComponentTree = new PEComponentTree(peDetailsPanel);
        peDetailsPanel.setRegionNavigator(this::showInTree);
        visualizerPanel.setRegionNavigator(this::showInTree);
        visualizerPanel.setOpenVisualizationAction(this::showInVisualization);
        fileContent.setOpenVisualizationAction(this::showInVisualization);

        initGUI();
        initDropTargets();
        checkForUpdate();
        setToHex(settings.valueEquals(PortexSettingsKey.VALUES_AS_HEX, "1"));
    }

    private void checkForUpdate() {
        // only update if setting does not prevent it
        if(settings.valueEquals(PortexSettingsKey.DISABLE_UPDATE,"1")) {
            return;
        }
        UpdateWorker updater = new UpdateWorker();
        updater.execute();
    }

    public void refreshSelection() {
        peComponentTree.refreshSelection();
    }

    /**
     * Shows a structure that the user picked in the visualization in the tree and the details panel, and the
     * clicked offset in the content preview.
     */
    private void showInTree(FileRegion region, long offset) {
        // the details panel navigates to the start of the structure, which must not replace the user's selection
        visModel.withoutReveal(() -> peComponentTree.selectNodeFor(region));
        fileContent.showContentAtOffset(offset, visModel);
    }

    /**
     * Opens the visualization zoomed in to the offset, e.g., after a double click into the overview at the side.
     */
    private void showInVisualization(long offset) {
        peComponentTree.selectVisualization();
        fileContent.showContentAtOffset(offset, visModel);
        // after the layout, the visualization gets wider when it is shown, because the side panel is hidden
        invokeLater(() -> peDetailsPanel.zoomVisualizationTo(offset));
    }

    private static class UpdateWorker extends SwingWorker<Boolean, Void> {
        @Override
        protected Boolean doInBackground() {
            try {
                URL githubURL = new URL(versionURL);
                try (InputStreamReader is = new InputStreamReader(getClass().getResourceAsStream(currVersion), StandardCharsets.UTF_8);
                     BufferedReader versionIn = new BufferedReader(is);
                     Scanner s = new Scanner(githubURL.openStream())) {

                    int versionHere = Integer.parseInt(versionIn.readLine().trim());
                    int githubVersion = s.nextInt();

                    if (versionHere < githubVersion) {
                        return true;
                    }
                }
            } catch (UnknownHostException e) {
                LOGGER.info("unknown host or no internet connection: " + e.getMessage());
            } catch (IOException | NumberFormatException e) {
                LOGGER.error(e);
                e.printStackTrace();
            }
            return false;
        }

        protected void done() {
            try {
                if (get()) {
                    LOGGER.debug("update requested");
                    String message = "A new version is available. Do you want to download it?";
                    int response = JOptionPane.showConfirmDialog(null,
                            message,
                            "Update available",
                            JOptionPane.YES_NO_OPTION);
                    if (response == JOptionPane.YES_OPTION) {
                        openWebpage(new URL(releasePage));
                    }
                } else {
                    LOGGER.debug("no update necessary");
                }
            } catch (InterruptedException | ExecutionException | MalformedURLException e) {
                LOGGER.error(e);
            }
        }
    }

    private static boolean openWebpage(URI uri) {
        Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
            try {
                desktop.browse(uri);
                return true;
            } catch (IOException e) {
                LOGGER.error(e);
                e.printStackTrace();
            }
        }
        return false;
    }

    private static boolean openWebpage(URL url) {
        try {
            return openWebpage(url.toURI());
        } catch (URISyntaxException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }
        return false;
    }

    private void initDropTargets() {
        // file drag and drop support
        this.setDropTarget(new FileDropper());
        peDetailsPanel.setDropTarget(new FileDropper());
        visualizerPanel.setDropTarget(new FileDropper());
        filePathLabel.setDropTarget(new FileDropper());
    }

    private void loadFile(File file) {
        PELoadWorker worker = new PELoadWorker(file, this, progressText);
        worker.addPropertyChangeListener(evt -> {
            String name = evt.getPropertyName();
            if (name.equals("progress")) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
                progressBar.setString(progress + " %");
                progressBarFrame.toFront();
                progressBarFrame.setAlwaysOnTop(true);
            } else if (name.equals("state")) {
                SwingWorker.StateValue state = (SwingWorker.StateValue) evt
                        .getNewValue();
                if (state == DONE) {
                    progressBarFrame.setVisible(false);
                }
            }
        });
        progressBarFrame.setVisible(true);
        WorkerKiller.getInstance().cancelAndDeleteWorkers();
        WorkerKiller.getInstance().addWorker(worker); // need to add this in case a user loads another PE during load process
        worker.execute();
    }

    public void setPeData(FullPEData data) {
        this.pedata = data;
        // first, so that the visualization knows the file when the other panels navigate to offsets
        visModel.load(pedata);
        peDetailsPanel.setPeData(pedata);
        peComponentTree.setPeData(pedata);
        peComponentTree.setSelectionRow(0);
        fileContent.setPeData(pedata);
    }

    private void initGUI() {
        // Main frame settings
        setSize(1280, 768);
        setLocationRelativeTo(null);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                visModel.dispose(); // stops the statistics and releases the file
            }
        });

        // Init main panel that holds everything
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // init Card Panel on the right
        this.cardPanel.add(visualizerPanel, "VISUALIZER");
        this.cardPanel.add(fileContent, "FILE_CONTENT");
        if(settings.valueEquals(PortexSettingsKey.CONTENT_PREVIEW, "1")) {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "FILE_CONTENT");
        } else {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "VISUALIZER");
        }
        // Add all other components
        panel.add(peDetailsPanel, BorderLayout.CENTER);
        panel.add(peComponentTree, BorderLayout.LINE_START);
        panel.add(cardPanel, BorderLayout.LINE_END);
        this.add(panel, BorderLayout.CENTER);

        initToolbar();
        initMenu();
        initProgressBar();
    }

    private void initToolbar() {
        JToolBar toolBar = new JToolBar();

        ImageIcon ico = ThemeIcon.HEX.load(darkTheme);
        JToggleButton hexButton = new JToggleButton(ico);
        hexButton.setSelected(settings.valueEquals(PortexSettingsKey.VALUES_AS_HEX, "1"));
        hexButton.addItemListener(e -> {
            int state = e.getStateChange();
            if (state == ItemEvent.SELECTED) {
                setToHex(true);
                settings.put(PortexSettingsKey.VALUES_AS_HEX, "1");
            } else if (state == ItemEvent.DESELECTED) {
                setToHex(false);
                settings.put(PortexSettingsKey.VALUES_AS_HEX, "0");
            }
            new WriteSettingsWorker(settings).execute();
        });
        ImageIcon imgIco = ThemeIcon.IMAGE.load(darkTheme);
        JToggleButton imgButton = new JToggleButton(imgIco);
        imgButton.setSelected(settings.valueEquals(PortexSettingsKey.CONTENT_PREVIEW, "0"));
        imgButton.addItemListener(e -> {
            int state = e.getStateChange();
            if(state == ItemEvent.SELECTED){
                ((CardLayout) cardPanel.getLayout()).show(cardPanel, "VISUALIZER");
                settings.put(PortexSettingsKey.CONTENT_PREVIEW, "0");
            } else {
                ((CardLayout) cardPanel.getLayout()).show(cardPanel, "FILE_CONTENT");
                settings.put(PortexSettingsKey.CONTENT_PREVIEW, "1");
            }
            cardPanel.revalidate(); // the cards have different widths
            cardPanel.repaint();
            fileContent.repaint();
            new WriteSettingsWorker(settings).execute();
        });

        toolBar.add(hexButton);
        toolBar.add(imgButton);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(filePathLabel);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.setOpaque(true);

        add(toolBar, BorderLayout.PAGE_START);

        toolBar.repaint();
    }

    private void setToHex(boolean hexEnabled) {
        peDetailsPanel.setHexEnabled(hexEnabled);
    }

    private void initProgressBar() {
        this.progressBarFrame = new JFrame("Loading PE file");
        AppIcon.applyTo(progressBarFrame);
        JPanel panel = new JPanel();
        this.progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(250, 25));
        progressBar.setIndeterminate(false);
        progressBar.setStringPainted(true);
        progressBar.setMaximum(100);

        panel.setLayout(new GridLayout(0, 1));
        panel.add(progressBar);

        // this panel makes sure the text is in the middle
        JPanel middleText = new JPanel();
        middleText.add(progressText);
        panel.add(middleText);

        progressBarFrame.add(panel);
        progressBarFrame.pack();
        progressBarFrame.setSize(400, 100);
        progressBarFrame.setLocationRelativeTo(null);
        progressBarFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }

    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu fileMenu = createFileMenu();
        JMenu help = createHelpMenu();
        JMenu settingsMenu = createSettingsMenu();

        menuBar.add(fileMenu);
        menuBar.add(settingsMenu);
        menuBar.add(help);

        this.setJMenuBar(menuBar);
    }

    private JMenu createSettingsMenu() {
        JMenu settingsMenu = new JMenu("Settings");
        JCheckBoxMenuItem disableUpdateCheck = new JCheckBoxMenuItem("Disable update check");
        JMenu themeMenu = new JMenu("Theme");
        JRadioButtonMenuItem darkTheme = new JRadioButtonMenuItem("Dark");
        JRadioButtonMenuItem brightTheme = new JRadioButtonMenuItem("Bright");
        ButtonGroup themes = new ButtonGroup();
        themes.add(darkTheme);
        themes.add(brightTheme);
        themeMenu.add(darkTheme);
        themeMenu.add(brightTheme);
        disableUpdateCheck.setState(settings.valueEquals(PortexSettingsKey.DISABLE_UPDATE, "1"));
        darkTheme.setSelected(this.darkTheme);
        brightTheme.setSelected(!this.darkTheme);

        settingsMenu.add(disableUpdateCheck);
        settingsMenu.add(themeMenu);

        disableUpdateCheck.addActionListener(e -> {
            if(disableUpdateCheck.getState()) {
                settings.put(PortexSettingsKey.DISABLE_UPDATE, "1");
            } else {
                settings.put(PortexSettingsKey.DISABLE_UPDATE, "0");
            }
            try {
                settings.writeSettings();
            } catch (IOException ex) {
                LOGGER.error(e);
            }
        });

        java.awt.event.ActionListener themeChanged = e -> {
            LookAndFeelSetting chosen = darkTheme.isSelected() ? LookAndFeelSetting.PORTEX : LookAndFeelSetting.SYSTEM;
            if (settings.valueEquals(PortexSettingsKey.LOOK_AND_FEEL, chosen.toString())) {
                return;
            }
            JOptionPane.showMessageDialog(this,
                    "Please restart PortexAnalyzerGUI for the new theme!",
                    "Theme changed",
                    JOptionPane.INFORMATION_MESSAGE);
            settings.put(PortexSettingsKey.LOOK_AND_FEEL, chosen.toString());
            try {
                settings.writeSettings();
            } catch (IOException ex) {
                LOGGER.error(ex);
            }
        };
        darkTheme.addActionListener(themeChanged);
        brightTheme.addActionListener(themeChanged);
        return settingsMenu;
    }

    private JMenu createHelpMenu() {
        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        help.add(about);
        about.addActionListener(arg0 -> invokeLater(() -> {
            AboutFrame aFrame = new AboutFrame();
            aFrame.setVisible(true);
        }));
        return help;
    }

    private JMenu createFileMenu() {
        // open file
        JMenu fileMenu = new JMenu("File");
        JMenuItem open = new JMenuItem("Open...", ThemeIcon.OPEN.load(darkTheme));
        fileMenu.add(open);
        open.addActionListener(arg0 -> {
            String path = PortexSwingUtils.getOpenFileNameFromUser(this);
            if (path != null) {
                filePathLabel.setText(path);
                loadFile(new File(path));
            }
        });

        // close application
        JMenuItem exit = new JMenuItem("Exit", ThemeIcon.EXIT.load(darkTheme));
        fileMenu.add(exit);
        exit.addActionListener(arg0 -> dispose());
        return fileMenu;
    }

    private class FileDropper extends DropTarget {
        @Override
        public synchronized void dragEnter(DropTargetDragEvent evt) {
            if (!evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                evt.rejectDrag(); // e.g., text that was dragged from a text component
            } else {
                super.dragEnter(evt);
            }
        }

        @Override
        public synchronized void dragOver(DropTargetDragEvent evt) {
            if (!evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                evt.rejectDrag();
            } else {
                super.dragOver(evt);
            }
        }

        @Override
        public synchronized void drop(DropTargetDropEvent evt) {
            if (!evt.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                evt.rejectDrop();
                return;
            }
            try {
                evt.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> droppedFiles = (List<File>)
                        evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                if (droppedFiles.size() > 0) {
                    File file = droppedFiles.get(0);
                    filePathLabel.setText(file.getAbsolutePath());
                    loadFile(file);
                }
            } catch (IOException | UnsupportedFlavorException ex) {
                String message = "Could not load PE file from dropper! Reason: " + ex.getMessage();
                LOGGER.error(message);
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        message,
                        "Unable to load",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
