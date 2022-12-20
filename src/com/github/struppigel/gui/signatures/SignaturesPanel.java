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
package com.github.struppigel.gui.signatures;

import com.github.struppigel.gui.FullPEData;
import com.github.struppigel.gui.PEFieldsTable;
import com.github.struppigel.gui.PortexSwingUtils;
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
    private FullPEData pedata;

    private String rulePath; // TODO set a default path
    private String yaraPath;

    // e.g. Yara|PEiD, XORedPE, Full file|EP
    private final String[] summaryHeaders = {"Source", "Match name", "Description", "Scan mode"};

    //e.g. XORedPE, "This program", "0xcafebabe", "Resource"
    private final String[] patternHeaders = {"Rule name", "Pattern", "Content", "Offset"}; //, "Location"};
    private boolean hexEnabled = true;
    private JTextField yaraPathTextField = new JTextField(30);
    private JTextField rulePathTextField = new JTextField(30);

    private List<YaraRuleMatch> scanResults = new ArrayList<>();

    public SignaturesPanel() {
        // nothing
    }

    private void initProgressBar() {
        this.removeAll();
        this.add(scanRunningBar);
        this.setLayout(new FlowLayout());
        scanRunningBar.setIndeterminate(true);
        scanRunningBar.setVisible(true);
        this.repaint();
    }

    void buildTables(List<YaraRuleMatch> scanResults) {
        this.scanResults = scanResults;
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


        JPanel tablePanel = new JPanel();
        tablePanel.setLayout(new GridLayout(0, 1));
        tablePanel.add(new JScrollPane(summaryTable));
        tablePanel.add(new JScrollPane(patternTable));
        this.setLayout(new BorderLayout());
        this.add(tablePanel, BorderLayout.CENTER);

        // set up buttons
        JPanel buttonPanel = new JPanel();
        JButton rescan = new JButton("Rescan");
        JButton pathSettings = new JButton("Settings");
        buttonPanel.add(rescan);
        buttonPanel.add(pathSettings);
        pathSettings.addActionListener(e -> requestPathes());
        rescan.addActionListener(e -> scan());

        this.add(buttonPanel, BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    private void fillTableModelsWithData(DefaultTableModel sumModel, DefaultTableModel patModel) {
        for (YaraRuleMatch match : this.scanResults) {
            sumModel.addRow(match.toSummaryRow());
            for (Object[] row : match.toPatternRows()) {
                patModel.addRow(row);
            }
        }
    }

    private void showNoMatchesIfNotFound() {
        if (scanResults.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No matches found",
                    "No matches",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }

    void requestPathes() {
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
        setButtonListeners(scanButton, yaraPathButton, rulePathButton);

        this.setLayout(new BorderLayout());
        this.add(tablePanel, BorderLayout.NORTH);
        this.add(Box.createVerticalGlue(), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void setButtonListeners(JButton scanButton, JButton yaraPathButton, JButton rulePathButton) {

        scanButton.addActionListener(e -> scan());

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
        } else {
            requestPathes();
        }

    }

    public void setPeData(FullPEData data) {
        this.pedata = data;
        scan();
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        buildTables(scanResults); // new renderer settings, refresh the panel
    }

}
