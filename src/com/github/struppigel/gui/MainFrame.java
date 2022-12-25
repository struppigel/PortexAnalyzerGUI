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
package com.github.struppigel.gui;

import com.github.struppigel.gui.pedetails.PEDetailsPanel;
import com.github.struppigel.gui.utils.PELoadWorker;
import com.github.struppigel.gui.utils.PortexSwingUtils;
import com.github.struppigel.gui.utils.WorkerKiller;
import com.github.struppigel.settings.PortexSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
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
    private final VisualizerPanel visualizerPanel;
    private final PEDetailsPanel peDetailsPanel;
    private final PortexSettings settings;
    private FullPEData pedata = null;
    private PEComponentTree peComponentTree;
    ;
    private JFrame progressBarFrame;
    private JProgressBar progressBar;
    private final static String versionURL = "https://github.com/struppigel/PortexAnalyzerGUI/raw/main/resources/upd_version.txt";
    private final static String currVersion = "/upd_version.txt";
    private final static String releasePage = "https://github.com/struppigel/PortexAnalyzerGUI/releases";
    private final JLabel progressText = new JLabel("Loading ...");

    public MainFrame(PortexSettings settings) {
        super("Portex Analyzer v. " + AboutFrame.version);

        this.settings = settings;
        visualizerPanel = new VisualizerPanel();
        peDetailsPanel = new PEDetailsPanel(visualizerPanel, this, settings);
        peComponentTree = new PEComponentTree(peDetailsPanel);

        initGUI();
        initDropTargets();
        checkForUpdate();
    }

    private void checkForUpdate() {
        UpdateWorker updater = new UpdateWorker();
        updater.execute();
    }

    public void refreshSelection() {
        peComponentTree.refreshSelection();
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
        try {
            this.pedata = data;
            visualizerPanel.visualizePE(pedata.getFile());
            peDetailsPanel.setPeData(pedata);
            peComponentTree.setPeData(pedata);
            peComponentTree.setSelectionRow(0);
        } catch (IOException e) {
            String message = "Could not load PE file! Reason: " + e.getMessage();
            LOGGER.error(message);
            e.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    message,
                    "Unable to load",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void initGUI() {
        // Main frame settings
        setSize(1024, 800);
        setLocationRelativeTo(null);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Init main panel that holds everything
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        // Add all other components
        panel.add(peDetailsPanel, BorderLayout.CENTER);
        panel.add(peComponentTree, BorderLayout.LINE_START);
        panel.add(visualizerPanel, BorderLayout.LINE_END);
        this.add(panel, BorderLayout.CENTER);

        /* Set up filepath
        JPanel inputPathPanel = new JPanel();
        inputPathPanel.add(filePathLabel);
        panel.add(inputPathPanel, BorderLayout.PAGE_START); */

        // set up toolbar
        JToolBar toolBar = new JToolBar();

        ImageIcon ico = new ImageIcon(getClass().getResource("/icons8-hexadecimal-24.png"));
        JToggleButton hexButton = new JToggleButton(ico);
        hexButton.setSelected(true);
        hexButton.addItemListener(e -> {
            int state = e.getStateChange();
            if (state == ItemEvent.SELECTED) {
                setToHex(true);
            } else if (state == ItemEvent.DESELECTED) {
                setToHex(false);
            }
        });
        toolBar.add(hexButton);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.add(filePathLabel);
        toolBar.add(Box.createHorizontalGlue());
        toolBar.setOpaque(true);


        add(toolBar, BorderLayout.PAGE_START);

/*
        toolBar.setUI ( new BasicToolBarUI() {
            @Override
            protected void paintDragWindow(Graphics g) {
                g.setColor(Color.blue);
                int w = dragWindow.getWidth();
                int h = dragWindow.getHeight();
                g.fillRect(0, 0, w, h);
                g.setColor(dragWindow.getBorderColor());
                g.drawRect(0, 0, w - 1, h - 1);
            }
        } );*/

        toolBar.repaint();

        initMenu();
        initProgressBar();
    }

    private void setToHex(boolean hexEnabled) {
        peDetailsPanel.setHexEnabled(hexEnabled);
    }

    private void initProgressBar() {
        this.progressBarFrame = new JFrame("Loading PE file");
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

        menuBar.add(fileMenu);
        menuBar.add(help);

        this.setJMenuBar(menuBar);
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
        JMenuItem open = new JMenuItem("Open...", new ImageIcon(getClass().getResource("/laptop-bug-icon.png")));
        fileMenu.add(open);
        open.addActionListener(arg0 -> {
            String path = PortexSwingUtils.getOpenFileNameFromUser(this);
            if (path != null) {
                filePathLabel.setText(path);
                loadFile(new File(path));
            }
        });

        // close application
        JMenuItem exit = new JMenuItem("Exit", new ImageIcon(getClass().getResource("/moon-icon.png")));
        fileMenu.add(exit);
        exit.addActionListener(arg0 -> dispose());
        return fileMenu;
    }

    private class FileDropper extends DropTarget {
        public synchronized void drop(DropTargetDropEvent evt) {
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
