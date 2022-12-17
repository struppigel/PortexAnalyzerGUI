/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import static javax.swing.SwingUtilities.invokeLater;


public class MainFrame extends JFrame {

    private static final Logger LOGGER = LogManager.getLogger();
    private final JLabel filePathLabel = new JLabel();

    private final VisualizerPanel visualizerPanel = new VisualizerPanel();
    private final PEDetailsPanel peDetailsPanel = new PEDetailsPanel();

    private FullPEData pedata = null;
    private PEComponentTree peComponentTree;
    private JFrame progressBarFrame;
    private JProgressBar progressBar;

    private String versionURL = "https://github.com/struppigel/PortexAnalyzerGUI/raw/main/resources/upd_version.txt";
    private String currVersion = "/upd_version.txt";

    public MainFrame() {
        super("Portex Analyzer v. " + AboutFrame.version);
        initGUI();
        initListener();
        checkForUpdate();
    }

    private void checkForUpdate() {
        // TODO SwingWorker
        try {
            URL githubURL = new URL(versionURL);

            try (InputStreamReader is = new InputStreamReader(getClass().getResourceAsStream(currVersion), StandardCharsets.UTF_8);
                 BufferedReader versIn = new BufferedReader(is);
                 Scanner s = new Scanner(githubURL.openStream());) {

                int versionHere = Integer.parseInt(versIn.readLine().trim());
                int githubVersion = s.nextInt();

                if(versionHere < githubVersion) {
                    // TODO request user to update
                    LOGGER.debug("update NOW NOW NOW");
                } else {
                    LOGGER.debug("NO NEED TO UPDATE");
                }
            } catch (IOException | NumberFormatException e) {
                LOGGER.error(e);
                e.printStackTrace();
            }

        } catch (MalformedURLException e) {
            LOGGER.error(e);
            e.printStackTrace();
        }

    }

    private void initListener() {
        // file drag and drop support
        this.setDropTarget(new FileDropper());
        peDetailsPanel.setDropTarget(new FileDropper());
        visualizerPanel.setDropTarget(new FileDropper());
        filePathLabel.setDropTarget(new FileDropper());
    }

    private void loadFile(File file) {
        PELoadWorker worker = new PELoadWorker(file, this);
        worker.addPropertyChangeListener(evt -> {
            String name = evt.getPropertyName();
            if (name.equals("progress")) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
            } else if (name.equals("state")) {
                SwingWorker.StateValue state = (SwingWorker.StateValue) evt
                        .getNewValue();
                switch (state) {
                    case DONE:
                        progressBarFrame.setVisible(false);
                        break;
                }
            }
        });
        progressBarFrame.setVisible(true);
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
        setSize(1024, 600);
        setLocationRelativeTo(null);
        setVisible(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel inputPathPanel = new JPanel();

        inputPathPanel.add(filePathLabel);

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(inputPathPanel, BorderLayout.PAGE_START);

        this.peComponentTree = new PEComponentTree(peDetailsPanel);

        panel.add(peDetailsPanel, BorderLayout.CENTER);
        panel.add(peComponentTree, BorderLayout.LINE_START);
        panel.add(visualizerPanel, BorderLayout.LINE_END);
        this.add(panel, BorderLayout.CENTER);
        initMenu();
        initProgressBar();
    }

    private void initProgressBar() {
        this.progressBarFrame = new JFrame();
        JPanel panel = new JPanel();
        JLabel label = new JLabel("Loading...");
        this.progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(250, 25));
        progressBar.setIndeterminate(false);
        int max = 100;
        progressBar.setMaximum(max);
        panel.add(label);
        panel.add(progressBar);
        progressBarFrame.add(panel);
        progressBarFrame.pack();
        progressBarFrame.setSize(400, 90);
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
            String path = getFileNameFromUser();
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

    private String getFileNameFromUser() {
        File userdir = new File(System.getProperty("user.dir"));
        JFileChooser fc = new JFileChooser(userdir);

        int state = fc.showOpenDialog(this);

        if (state == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (file != null) {
                return file.getAbsolutePath();
            }
        }
        return null;
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
