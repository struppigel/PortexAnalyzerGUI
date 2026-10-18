/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
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

import io.github.struppigel.parser.IOUtil;
import io.github.struppigel.gui.FileData;
import io.github.struppigel.gui.utils.HexFont;
import io.github.struppigel.gui.utils.OffsetListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import java.awt.*;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.LongConsumer;

/**
 * Shows the bytes of the file at an offset as characters, non-printable bytes as dots. Every navigation to an
 * offset in the GUI goes through this panel, so it tells its offset listeners about it.
 */
public class FileContentPreviewPanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int PREVIEW_SIZE = 0x2000;
    /** the width of the panel in characters, the text wraps at it */
    static final int COLUMNS = 24;
    // the accent of the themes, if the look and feel has none
    private static final Color DEFAULT_ACCENT = new Color(0xAA0000);

    private final JTextArea contentDisplay = new JTextArea("Offset: 0");
    private final List<OffsetListener> offsetListeners = new ArrayList<>();
    private LongConsumer openVisualization;

    private FileData fileData;
    private boolean isHexEnabled = true;
    private long currentOffset = 0;
    private long requestCount = 0;
    // what the text area shows: a header line, then one character per byte
    private long shownOffset = -1;
    private int shownLength = 0;
    private int headerLength = 0;
    private long highlightStart = -1;
    private long highlightEnd = -1;

    public FileContentPreviewPanel() {
        contentDisplay.setEditable(false);
        contentDisplay.setLineWrap(true);
        contentDisplay.setWrapStyleWord(false); // wraps between any two bytes
        contentDisplay.setFont(HexFont.font(Font.PLAIN, 12));
        contentDisplay.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && openVisualization != null) {
                    long offset = offsetAtTextPosition(contentDisplay.viewToModel(e.getPoint()));
                    if (offset >= 0) {
                        openVisualization.accept(offset);
                    }
                }
            }
        });
        this.setLayout(new BorderLayout());
        JScrollPane scrollPane = new JScrollPane(contentDisplay,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        this.add(scrollPane, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredSize() {
        // a fixed width, independent of the content
        FontMetrics metrics = contentDisplay.getFontMetrics(contentDisplay.getFont());
        int scrollBar = UIManager.getInt("ScrollBar.width") > 0 ? UIManager.getInt("ScrollBar.width") : 17;
        Insets insets = contentDisplay.getInsets();
        int width = metrics.charWidth('0') * COLUMNS + insets.left + insets.right + scrollBar;
        return new Dimension(width, super.getPreferredSize().height);
    }

    public void setFileData(FileData data) {
        this.fileData = data;
        showContentAtOffset(0);
    }

    /**
     * @param listener notified whenever the panel is asked to show an offset
     */
    public void addOffsetListener(OffsetListener listener) {
        offsetListeners.add(listener);
    }

    public void removeOffsetListener(OffsetListener listener) {
        offsetListeners.remove(listener);
    }

    /**
     * @param action opens the visualization zoomed in to the offset, called on a double click on a byte
     */
    public void setOpenVisualizationAction(LongConsumer action) {
        this.openVisualization = action;
        contentDisplay.setToolTipText(action == null ? null : "Double-click a byte to show it in the visualization");
    }

    /**
     * @param position a position in the shown text
     * @return the offset of the byte there, the first shown byte for the header; -1 if nothing is shown
     */
    long offsetAtTextPosition(int position) {
        if (shownOffset < 0 || shownLength == 0 || position < 0) {
            return -1;
        }
        long index = Math.max(0, Math.min(shownLength - 1, position - headerLength));
        return shownOffset + index;
    }

    /**
     * @return the offset the panel was last asked to show
     */
    public long getCurrentOffset() {
        return currentOffset;
    }

    public void showContentAtOffset(long offset) {
        showContentAtOffset(offset, null);
    }

    /**
     * @param source who navigates to the offset, passed to the offset listeners
     */
    public void showContentAtOffset(long offset, Object source) {
        currentOffset = offset;
        for (OffsetListener listener : new ArrayList<>(offsetListeners)) {
            listener.offsetChanged(offset, source);
        }
        if (fileData == null) {return;}
        final long request = ++requestCount;
        final boolean hex = isHexEnabled;
        (new SwingWorker<byte[], Void>() {

            @Override
            protected byte[] doInBackground() {
                return readContentAtOffset(offset);
            }

            protected void done() {
                if (request != requestCount) {
                    return; // a newer request shows another offset
                }
                try {
                    byte[] content = get();
                    String header = (hex ? "Offset: 0x" + Long.toHexString(offset) : "Offset: " + offset) + "\n";
                    contentDisplay.setText(header + printable(content));
                    contentDisplay.setCaretPosition(0);
                    shownOffset = offset;
                    shownLength = content.length;
                    headerLength = header.length();
                    applyHighlight();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.error(e);
                }
            }
        }).execute();
    }

    /**
     * Highlights bytes, e.g., the structure that is selected in the visualization.
     *
     * @param from the first offset, -1 for no highlight
     * @param toExclusive the end offset
     */
    public void setHighlight(long from, long toExclusive) {
        highlightStart = from;
        highlightEnd = toExclusive;
        applyHighlight();
    }

    private void applyHighlight() {
        contentDisplay.getHighlighter().removeAllHighlights();
        if (highlightStart < 0 || shownOffset < 0 || highlightEnd <= highlightStart) {
            return;
        }
        long from = Math.max(highlightStart, shownOffset) - shownOffset;
        long to = Math.min(highlightEnd, shownOffset + shownLength) - shownOffset;
        if (to <= from) {
            return;
        }
        try {
            // one character per byte, so the bytes are one range of the text
            contentDisplay.getHighlighter().addHighlight(headerLength + (int) from, headerLength + (int) to,
                    new DefaultHighlighter.DefaultHighlightPainter(highlightColorFor(contentDisplay.getForeground())));
        } catch (BadLocationException e) {
            LOGGER.warn("Could not highlight the bytes: " + e.getMessage());
        }
    }

    /**
     * @return a highlight in the accent color of the theme, on which text of the given color is readable: dark
     * behind light text, light behind dark text
     */
    static Color highlightColorFor(Color text) {
        Color accent = UIManager.getColor("Component.accentColor");
        if (accent == null) {
            accent = DEFAULT_ACCENT;
        }
        double luminance = (0.299 * text.getRed() + 0.587 * text.getGreen() + 0.114 * text.getBlue()) / 255;
        Color other = luminance > 0.5 ? Color.black : Color.white;
        double accentShare = luminance > 0.5 ? 0.85 : 0.3;
        return new Color((int) Math.round(accent.getRed() * accentShare + other.getRed() * (1 - accentShare)),
                (int) Math.round(accent.getGreen() * accentShare + other.getGreen() * (1 - accentShare)),
                (int) Math.round(accent.getBlue() * accentShare + other.getBlue() * (1 - accentShare)));
    }

    /**
     * @return one character per byte: printable ASCII as is, everything else as dot
     */
    static String printable(byte[] bytes) {
        char[] chars = new char[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            chars[i] = b >= 0x20 && b < 0x7f ? (char) b : '.';
        }
        return new String(chars);
    }

    /**
     * @return the shown text, for tests
     */
    String getText() {
        return contentDisplay.getText();
    }

    /**
     * @return the number of highlighted text ranges, for tests
     */
    int highlightCount() {
        return contentDisplay.getHighlighter().getHighlights().length;
    }

    private byte[] readContentAtOffset(long offset) {
        try (RandomAccessFile raf = new RandomAccessFile(fileData.getFile(), "r")) {
            return IOUtil.loadBytesSafely(offset, PREVIEW_SIZE, raf);
        } catch (IOException e) {
            LOGGER.error(e);
        }
        return new byte[0];
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.isHexEnabled = hexEnabled;
    }
}
