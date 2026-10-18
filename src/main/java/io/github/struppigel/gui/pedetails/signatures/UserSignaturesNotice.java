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
package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.gui.usersignatures.UserSignatureMatches;
import io.github.struppigel.gui.usersignatures.UserSignatureSet;
import io.github.struppigel.gui.utils.PortexSwingUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Shows which of the user's signatures were scanned with, and the entries that were skipped or are slow to scan.
 */
public class UserSignaturesNotice extends JPanel {
    private final JLabel status = new JLabel();
    private final JLabel warningTitle = new JLabel();
    private final JTextArea warnings = new JTextArea(5, 60);
    private final JScrollPane warningsScroll = new JScrollPane(warnings);

    public UserSignaturesNotice() {
        super(new BorderLayout(0, 4));
        PortexSwingUtils.disableHtml(status);
        PortexSwingUtils.disableHtml(warningTitle);
        Color warningColor = UIManager.getColor("Actions.Yellow");
        if (warningColor != null) {
            warningTitle.setForeground(warningColor);
        }
        // copyable, so that the user can look up the file and line
        warnings.setEditable(false);
        warnings.setLineWrap(false);
        JPanel labels = new JPanel(new GridLayout(0, 1));
        labels.add(status);
        labels.add(warningTitle);
        add(labels, BorderLayout.NORTH);
        add(warningsScroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        showSignatures(UserSignatureMatches.NONE);
    }

    /**
     * Shows the state of the user's signatures that the file was scanned with, hides the notice if there are none.
     */
    public void showSignatures(UserSignatureMatches matches) {
        showSignatures(matches.getSignatures(), matches.getError());
    }

    /**
     * Shows the state of the user's signatures, hides the notice if there are none.
     *
     * @param scanError why the scan with the signatures failed, empty if it succeeded
     */
    public void showSignatures(UserSignatureSet signatures, Optional<String> scanError) {
        if (signatures.getFolder() == null) {
            setVisible(false);
            return;
        }
        setVisible(true);
        status.setText(statusText(signatures));
        status.setToolTipText(signatures.getFolder().getAbsolutePath());

        List<String> lines = new ArrayList<>();
        scanError.ifPresent(lines::add);
        lines.addAll(signatures.getLoadWarnings());
        lines.addAll(signatures.getScanWarnings());
        warningTitle.setText(warningTitleText(signatures, scanError));
        warningTitle.setVisible(!lines.isEmpty());
        warnings.setText(String.join("\n", lines));
        warnings.setCaretPosition(0);
        warningsScroll.setVisible(!lines.isEmpty());
        revalidate();
    }

    static String statusText(UserSignatureSet signatures) {
        String folder = signatures.getFolder().getAbsolutePath();
        if (signatures.getSignatureCount() == 0) {
            return "Your signatures: none, add .sig files to " + folder;
        }
        int files = signatures.getFileCount();
        return "Your signatures: " + signatures.getSignatureCount() + " from " + files
                + (files == 1 ? " file" : " files") + " in " + folder;
    }

    private static String warningTitleText(UserSignatureSet signatures, Optional<String> scanError) {
        if (scanError.isPresent()) {
            return "Your signatures could not be scanned:";
        }
        if (signatures.hasSkippedSignatures()) {
            return "Some of your signatures were skipped:";
        }
        return "Some of your signatures are slow to scan:";
    }

    String getStatusText() {
        return status.getText();
    }

    String getWarningTitle() {
        return warningTitle.isVisible() ? warningTitle.getText() : "";
    }

    String getWarningsText() {
        return warningsScroll.isVisible() ? warnings.getText() : "";
    }
}
