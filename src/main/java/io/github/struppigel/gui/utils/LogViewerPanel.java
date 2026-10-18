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
package io.github.struppigel.gui.utils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * Shows the content of the log file, so that the user can copy it into a bug report.
 */
public class LogViewerPanel extends JPanel {

    /** larger logs are shown from the end, which has the latest messages */
    static final long MAX_BYTES = 1024 * 1024;

    private final File logFile;
    private final JLabel header = new JLabel();
    private final JTextArea text = new JTextArea();
    private final String logPath;

    public LogViewerPanel(File logFile, String summary) {
        super(new BorderLayout(0, 6));
        this.logFile = logFile;
        this.logPath = logFile.getAbsolutePath();
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JButton copyButton = new JButton("Copy to clipboard");
        copyButton.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.getText()), null));
        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refresh());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(copyButton);
        buttons.add(refreshButton);
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(text), BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setSummary(summary);
        refresh();
    }

    public final void setSummary(String summary) {
        header.setText("<html>" + escape(summary) + "<br>Log file: " + escape(logPath) + "</html>");
    }

    /**
     * Reads the log file again and scrolls to its end.
     */
    public final void refresh() {
        text.setText(readTail(logFile, MAX_BYTES));
        text.setCaretPosition(text.getDocument().getLength());
    }

    String getText() {
        return text.getText();
    }

    /**
     * @param maxBytes the most bytes to read from the end of the file
     * @return the content of the file, starting with a note and at a line start if the file was cut, or a message if
     * the file can not be read
     */
    static String readTail(File file, long maxBytes) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, raf.length() - maxBytes);
            byte[] bytes = new byte[(int) (raf.length() - start)];
            raf.seek(start);
            raf.readFully(bytes);
            String content = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) {
                int lineStart = content.indexOf('\n') + 1;
                content = "[Only the last " + maxBytes / 1024 + " KiB of the log are shown]\n"
                        + content.substring(lineStart);
            }
            return content;
        } catch (IOException e) {
            return "Could not read the log file " + file.getAbsolutePath() + ": " + e;
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
