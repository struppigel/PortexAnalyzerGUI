/**
 * *****************************************************************************
 * Copyright 2026 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package io.github.struppigel.gui.pedetails;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.PEFieldsTable;
import io.github.struppigel.gui.utils.InfoCard;
import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.parser.sections.cert.AuthenticodeCertificate;
import io.github.struppigel.parser.sections.cert.AuthenticodeSignature;
import io.github.struppigel.parser.sections.cert.AuthenticodeVerification;
import io.github.struppigel.parser.sections.cert.CertificateTable;
import io.github.struppigel.parser.sections.cert.VerificationResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.dnd.DropTarget;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Shows the Authenticode signatures of the file: a summary, one tab per signature with its properties and
 * certificates, the entries of the certificate table and the issues found while parsing and verifying.
 */
public class AuthenticodePanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    static final String TABLE_TAB = "Table";
    static final String ISSUES_TAB = "Issues";

    private final FileContentPreviewPanel previewPanel;
    private final InfoCard summary = new InfoCard();
    private final JTabbedPane tabs = new JTabbedPane();
    // the signature of each tab, in the order of the tabs; the other tabs follow
    private List<AuthenticodeSignature> signatures = new ArrayList<>();
    private CertificateTable shownTable;

    public AuthenticodePanel(FileContentPreviewPanel previewPanel) {
        super(new BorderLayout());
        this.previewPanel = previewPanel;
        add(summary, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Shows the signatures of the file. Keeps the selected tab if the file is the same, e.g., after switching
     * between hex and decimal.
     */
    public void show(FullPEData data, boolean hexEnabled) {
        Optional<CertificateTable> maybeTable = data.getCertificateTable();
        if (!maybeTable.isPresent()) {
            clear();
            return;
        }
        CertificateTable table = maybeTable.get();
        Optional<AuthenticodeVerification> verification = data.getAuthenticodeVerification();
        String selectedTitle = table == shownTable && tabs.getSelectedIndex() >= 0
                ? tabs.getTitleAt(tabs.getSelectedIndex()) : null;
        tabs.removeAll();
        shownTable = table;
        signatures = table.getAllSignatures();
        showSummary(AuthenticodeTables.summary(table, verification, hexEnabled));
        for (AuthenticodeSignature signature : signatures) {
            tabs.addTab(AuthenticodeTables.title(signature),
                    createSignatureTab(data.getFile(), signature, AuthenticodeTables.resultFor(signature, verification),
                            hexEnabled));
        }
        tabs.addTab(TABLE_TAB, new JScrollPane(createTable(AuthenticodeTables.entryRows(table),
                AuthenticodeTables.ENTRY_HEADER, AuthenticodeTables.ENTRY_OFFSET_COLUMN, hexEnabled)));
        List<Object[]> issues = AuthenticodeTables.issueRows(table, verification);
        PEFieldsTable issueTable = createTable(issues, AuthenticodeTables.ISSUE_HEADER,
                AuthenticodeTables.ISSUE_OFFSET_COLUMN, hexEnabled);
        issueTable.getColumnModel().getColumn(0).setPreferredWidth(500);
        issueTable.setWrappedColumn(0);
        tabs.addTab(ISSUES_TAB + " (" + issues.size() + ")", new JScrollPane(issueTable));
        if (selectedTitle != null && tabs.indexOfTab(selectedTitle) >= 0) {
            tabs.setSelectedIndex(tabs.indexOfTab(selectedTitle));
        }
        revalidate();
        repaint();
    }

    private void clear() {
        tabs.removeAll();
        summary.clear().render();
        signatures = new ArrayList<>();
        shownTable = null;
    }

    private JPanel createSignatureTab(File peFile, AuthenticodeSignature signature,
                                      Optional<VerificationResult> result, boolean hexEnabled) {
        PEFieldsTable fields = createTable(AuthenticodeTables.signatureRows(signature, result),
                AuthenticodeTables.SIGNATURE_HEADER, AuthenticodeTables.SIGNATURE_OFFSET_COLUMN, hexEnabled);
        fields.getColumnModel().getColumn(1).setPreferredWidth(450);
        List<AuthenticodeCertificate> certificates = AuthenticodeTables.certificates(signature);
        PEFieldsTable certificateTable = createTable(AuthenticodeTables.certificateRows(signature, result),
                AuthenticodeTables.CERTIFICATE_HEADER, AuthenticodeTables.CERTIFICATE_OFFSET_COLUMN, hexEnabled);

        JButton saveButton = new JButton("Save certificate");
        saveButton.setEnabled(false);
        certificateTable.getSelectionModel().addListSelectionListener(
                e -> saveButton.setEnabled(certificateTable.getSelectedRow() >= 0));
        saveButton.addActionListener(e -> {
            int row = certificateTable.getSelectedRow();
            if (row >= 0) {
                int index = certificateTable.convertRowIndexToModel(row);
                saveCertificate(certificates.get(index), defaultFileName(peFile, certificates.get(index), index));
            }
        });
        JPanel certificatePanel = new JPanel(new BorderLayout());
        certificatePanel.add(new JScrollPane(certificateTable), BorderLayout.CENTER);
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(saveButton);
        certificatePanel.add(buttonPanel, BorderLayout.PAGE_END);

        JPanel tab = new JPanel(new GridLayout(0, 1));
        tab.add(new JScrollPane(fields));
        tab.add(certificatePanel);
        return tab;
    }

    private PEFieldsTable createTable(List<Object[]> rows, String[] header, int offsetColumn, boolean hexEnabled) {
        PEFieldsTable table = new PEFieldsTable(hexEnabled);
        DefaultTableModel model = new PEFieldsTable.PETableModel();
        model.setColumnIdentifiers(header);
        for (Object[] row : rows) {
            model.addRow(row);
        }
        table.setModel(model);
        table.setPreviewOffsetColumn(offsetColumn, previewPanel);
        return table;
    }

    /**
     * Selects the tab of the innermost signature at the offset, e.g., after the user picked it in the
     * visualization. Keeps the tab if no signature is there.
     */
    public void selectSignatureAt(long offset) {
        if (shownTable == null) {
            return;
        }
        Optional<AuthenticodeSignature> signature = AuthenticodeTables.innermostSignatureAt(shownTable, offset);
        if (signature.isPresent()) {
            int index = signatures.indexOf(signature.get());
            if (index >= 0 && index < tabs.getTabCount()) {
                tabs.setSelectedIndex(index);
            }
        }
    }

    @Override
    public synchronized void setDropTarget(DropTarget dt) {
        summary.setDropTarget(dt);
    }

    /**
     * @return the titles of the tabs, for tests
     */
    List<String> getTabTitles() {
        List<String> titles = new ArrayList<>();
        for (int i = 0; i < tabs.getTabCount(); i++) {
            titles.add(tabs.getTitleAt(i));
        }
        return titles;
    }

    /**
     * @return the title of the selected tab, null if there is none; for tests
     */
    String getSelectedTabTitle() {
        return tabs.getSelectedIndex() < 0 ? null : tabs.getTitleAt(tabs.getSelectedIndex());
    }

    void selectTab(String title) {
        tabs.setSelectedIndex(tabs.indexOfTab(title));
    }

    String getSummaryText() {
        return summary.getPlainText();
    }

    /**
     * Shows the lines of the summary as rows, the integrity as a colored badge.
     */
    private void showSummary(String text) {
        summary.clear();
        for (String line : text.split("\r?\n")) {
            int colon = line.indexOf(": ");
            if (colon <= 0) {
                summary.paragraph(line);
                continue;
            }
            String label = line.substring(0, colon);
            String value = line.substring(colon + 2);
            if ("Integrity".equals(label)) {
                summary.badgeRow(label, value, AuthenticodeTables.kindOf(value));
            } else {
                summary.row(label, value);
            }
        }
        summary.render();
    }

    /**
     * @return the suggested file for the certificate: next to the PE file, named after the subject
     */
    static String defaultFileName(File peFile, AuthenticodeCertificate certificate, int index) {
        String name = AuthenticodeTables.subject(certificate).replaceAll("[^A-Za-z0-9._-]+", "_");
        if (name.replace("_", "").isEmpty()) {
            name = "certificate" + index;
        }
        return peFile.getAbsolutePath() + "." + name + ".cer";
    }

    /**
     * Writes the DER encoding of the certificate, which is the content of a .cer file.
     */
    static void writeCertificate(AuthenticodeCertificate certificate, File file) throws IOException {
        Files.write(file.toPath(), certificate.getEncoded());
    }

    private void saveCertificate(AuthenticodeCertificate certificate, String defaultFileName) {
        String path = PortexSwingUtils.getSaveFileNameFromUser(this, defaultFileName);
        if (path == null) {
            return; // cancelled
        }
        File file = new File(path);
        if (!PortexSwingUtils.checkIfFileExistsAndAskIfOverwrite(this, file)) {
            return;
        }
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    writeCertificate(certificate, file);
                    return null;
                } catch (IOException e) {
                    LOGGER.error(e);
                    return e.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String error = get();
                    if (error == null) {
                        JOptionPane.showMessageDialog(AuthenticodePanel.this,
                                "File successfully saved to " + file,
                                "Success",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(AuthenticodePanel.this,
                                "Unable to save file :(. " + error,
                                "Something went wrong",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (ExecutionException | InterruptedException e) {
                    LOGGER.error(e);
                }
            }
        }.execute();
    }
}
