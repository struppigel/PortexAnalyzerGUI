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
package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.PEFieldsTable;
import io.github.struppigel.gui.pedetails.FileContentPreviewPanel;
import io.github.struppigel.gui.utils.WorkerKiller;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class SignaturesPanel extends JPanel {
    private static final Logger LOGGER = LogManager.getLogger();
    private final JProgressBar scanRunningBar = new JProgressBar();
    private final FileContentPreviewPanel previewPanel;
    private FullPEData pedata;

    // e.g. PEiD, Filetype
    private final String[] summaryHeaders = {"Source", "Match name", "Scan mode"};

    //e.g. "This program", "0xcafebabe", "Resource"
    private final String[] patternHeaders = {"Match name", "Pattern name", "Pattern content", "Offset", "Location"};
    private boolean hexEnabled = true;

    private List<PEidRuleMatch> peidResults = null;

    public SignaturesPanel(FileContentPreviewPanel previewPanel) {
        this.previewPanel = previewPanel;
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
        this.removeAll(); //remove progress bar

        PEFieldsTable summaryTable = new PEFieldsTable(hexEnabled);
        PEFieldsTable patternTable = new PEFieldsTable(hexEnabled);
        patternTable.setPreviewOffsetColumn(3, previewPanel);

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
        buttonPanel.add(rescan);
        rescan.addActionListener(e -> scan());

        this.add(buttonPanel, BorderLayout.SOUTH);

        revalidate();
        repaint();
    }

    private void fillTableModelsWithData(DefaultTableModel sumModel, DefaultTableModel patModel) {
        List<RuleMatch> allResults = new ArrayList<>();
        if(peidResults != null) allResults.addAll(peidResults);
        if(allResults.isEmpty()) {
            allResults.add(new EmptyRuleMatch());
        }
        for (RuleMatch match : allResults) {
            sumModel.addRow(match.toSummaryRow());
            for (Object[] row : match.toPatternRows()) {
                patModel.addRow(row);
            }
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

        }
        return -1;
    }

    private void scan() {
        initProgressBar();
        PEidScanner peidScanner = new PEidScanner(this, pedata);
        peidScanner.execute();
        WorkerKiller.getInstance().addWorker(peidScanner);
    }

    public void setPeData(FullPEData data) {
        this.pedata = data;
        scan();
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.hexEnabled = hexEnabled;
        if(pedata == null){return;}
        buildTables(); // new renderer settings, refresh the panel
    }

    public void buildPEiDTables(List<PEidRuleMatch> signatureMatches) {
        this.peidResults = signatureMatches;
        buildTables();
    }
}
