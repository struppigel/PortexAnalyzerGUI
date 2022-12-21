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
package com.github.struppigel.gui.pedetails.signatures;

import com.github.struppigel.gui.FullPEData;
import com.github.struppigel.gui.PEFieldsTable;
import com.github.struppigel.gui.utils.PortexSwingUtils;
import com.github.struppigel.gui.utils.WriteSettingsWorker;
import com.github.struppigel.settings.PortexSettings;
import com.github.struppigel.settings.PortexSettingsKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class SignaturesPanel extends JPanel {
    private static final Logger LOGGER = LogManager.getLogger();
    private final JProgressBar scanRunningBar = new JProgressBar();
    private final PortexSettings settings;
    private FullPEData pedata;

    private String rulePath; // TODO set a default path
    private String yaraPath;

    // e.g. Yara|PEiD, XORedPE, Full file|EP
    private final String[] summaryHeaders = {"Source", "Match name", "Scan mode"};

    //e.g. XORedPE, "This program", "0xcafebabe", "Resource"
    private final String[] patternHeaders = {"Rule name", "Pattern", "Content", "Offset"}; //, "Location"};
    private boolean hexEnabled = true;
    private JTextField yaraPathTextField = new JTextField(30);
    private JTextField rulePathTextField = new JTextField(30);

    private List<YaraRuleMatch> yaraResults = null;
    // TODO put this here in result table!
    private List<PEidRuleMatch> peidResults = null;
    public SignaturesPanel(PortexSettings settings) {
        this.settings = settings;
        applyLoadedSettings();
    }

    private void applyLoadedSettings() {
        if(settings.containsKey(PortexSettingsKey.YARA_PATH)) {
            yaraPath = settings.get(PortexSettingsKey.YARA_PATH);
            yaraPathTextField.setText(yaraPath);
        }
        if(settings.containsKey(PortexSettingsKey.YARA_SIGNATURE_PATH)) {
            rulePath = settings.get(PortexSettingsKey.YARA_SIGNATURE_PATH);
            rulePathTextField.setText(rulePath);
        }
    }

    private void initProgressBar() {
        this.removeAll();
        this.add(scanRunningBar);
        this.setLayout(new FlowLayout());
        scanRunningBar.setIndeterminate(true);
        scanRunningBar.setVisible(true);
        this.repaint();
    }

    private void buildTables() {
        showNoMatchesIfNotFound();
        this.removeAll(); //remove progress bar

        PEFieldsTable summaryTable = new PEFieldsTable(hexEnabled);
        PEFieldsTable patternTable = new PEFieldsTable(hexEnabled);

        DefaultTableModel sumModel = new PEFieldsTable.PETableModel();
        sumModel.setColumnIdentifiers(summaryHeaders);

        DefaultTableModel patModel = new PEFieldsTable.PETableModel();
        patModel.setColumnIdentifiers(patternHeaders);

        summaryTable.setModel(sumModel);
        patternTable.setModel(patModel);

        initListener(summaryTable, patternTable);

        fillTableModelsWithData(sumModel, patModel);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(summaryTable),
                new JScrollPane(patternTable));
        splitPane.setDividerLocation(200);

        this.setLayout(new BorderLayout());
        this.add(splitPane, BorderLayout.CENTER);

        // set up buttons
        JPanel buttonPanel = new JPanel();
        JButton rescan = new JButton("Rescan");
        JButton pathSettings = new JButton("Settings");
        buttonPanel.add(rescan);
        buttonPanel.add(pathSettings);
        pathSettings.addActionListener(e -> requestPaths());
        rescan.addActionListener(e -> scan());

        this.add(buttonPanel, BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    private void fillTableModelsWithData(DefaultTableModel sumModel, DefaultTableModel patModel) {
        List<RuleMatch> allResults = new ArrayList<>();
        allResults.addAll(yaraResults);
        allResults.addAll(peidResults);
        for (RuleMatch match : allResults) {
            sumModel.addRow(match.toSummaryRow());
            for (Object[] row : match.toPatternRows()) {
                patModel.addRow(row);
            }
        }
    }

    private void showNoMatchesIfNotFound() {
        if (yaraResults.isEmpty() && peidResults.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No matches found",
                    "No matches",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    void requestPaths() {
        this.removeAll();

        JPanel tablePanel = new JPanel();
        tablePanel.setLayout(new GridLayout(0, 1));

        JButton yaraPathButton = new JButton("...");
        JButton rulePathButton = new JButton("...");

        JPanel demand = new JPanel();
        demand.add(new JLabel("Add your yara and signature paths for signature scanning"));
        tablePanel.add(demand);
        JPanel firstRow = new JPanel();
        firstRow.setLayout(new FlowLayout());
        firstRow.add(new JLabel("Yara path:"));
        firstRow.add(yaraPathTextField);
        firstRow.add(yaraPathButton);
        tablePanel.add(firstRow);
        JPanel secondRow = new JPanel();
        secondRow.setLayout(new FlowLayout());
        secondRow.add(new JLabel("Rule path:"));
        secondRow.add(rulePathTextField);
        secondRow.add(rulePathButton);
        tablePanel.add(secondRow);
        JButton scanButton = new JButton("Scan");
        JPanel thirdRow = new JPanel();
        thirdRow.add(scanButton);
        tablePanel.add(thirdRow);
        setButtonListenersForRequestPath(scanButton, yaraPathButton, rulePathButton);

        this.setLayout(new BorderLayout());
        this.add(tablePanel, BorderLayout.NORTH);
        this.add(Box.createVerticalGlue(), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void setButtonListenersForRequestPath(JButton scanButton, JButton yaraPathButton, JButton rulePathButton) {

        scanButton.addActionListener(e -> {
            writeSettings();
            scan();
        });

        yaraPathButton.addActionListener(e -> {
            String result = PortexSwingUtils.getOpenFileNameFromUser(this);
            if(result != null) {
                yaraPath = result;
                yaraPathTextField.setText(yaraPath);
            }


        });
        rulePathButton.addActionListener(e -> {
            String result = PortexSwingUtils.getOpenFileNameFromUser(this);
            if(result != null) {
                rulePath = result;
                rulePathTextField.setText(rulePath);
            }
        });
    }

    private void writeSettings() {
        if (yaraPath != null && rulePath != null && new File(yaraPath).exists() && new File(rulePath).exists()) {
            settings.put(PortexSettingsKey.YARA_PATH, yaraPath);
            settings.put(PortexSettingsKey.YARA_SIGNATURE_PATH, rulePath);
            new WriteSettingsWorker(settings).execute();
        }
    }

    private void initListener(JTable summaryTable, JTable patternTable) {
        ListSelectionModel model = summaryTable.getSelectionModel();
        model.addListSelectionListener(e -> {
            String rule = getRuleNameForSelectedRow(summaryTable);
            LOGGER.info(rule + " selected");
            // filters exact matches with rule name at column 0
            RowFilter<PEFieldsTable.PETableModel, Object> rf =
                    new RowFilter() {
                        @Override
                        public boolean include(Entry entry) {
                            if (rule == null || entry == null) return true;
                            return rule.equals(entry.getStringValue(0));
                        }
                    };
            ((TableRowSorter) patternTable.getRowSorter()).setRowFilter(rf);
        });
    }

    private String getRuleNameForSelectedRow(JTable table) {
        final int RULE_NAME_COL = 1;
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        int row = getSelectedRow(table);
        if (row == -1) return null;
        Vector vRow = (Vector) model.getDataVector().elementAt(row);
        Object rule = vRow.elementAt(RULE_NAME_COL);
        return rule.toString();
    }

    /**
     * Return a vector of the data in the currently selected row. Returns null if no row selected
     *
     * @param table
     * @return vector of the data in the currently selected row or null if nothing selected
     */
    private int getSelectedRow(JTable table) {

        int rowIndex = table.getSelectedRow();
        if (rowIndex >= 0) {
            return table.convertRowIndexToModel(rowIndex);
            //

        }
        return -1;
    }

    private void scan() {
        if (yaraPath != null && rulePath != null && new File(yaraPath).exists() && new File(rulePath).exists()) {
            initProgressBar();
            new YaraScanner(this, pedata, yaraPath, rulePath).execute();
            new PEidScanner(this, pedata).execute();
        } else {
            requestPaths();
        }

    }

    public void setPeData(FullPEData data) {
        this.pedata = data;
        scan();
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        buildTables(); // new renderer settings, refresh the panel
    }

    public void buildPEiDTables(List<PEidRuleMatch> signatureMatches) {
        this.peidResults = signatureMatches;
        if(yaraResults != null) {
            buildTables();
        }
    }


    void buildYaraTables(List<YaraRuleMatch> scanResults) {
        this.yaraResults = scanResults;
        if(peidResults != null) {
            buildTables();
        }
    }
}
