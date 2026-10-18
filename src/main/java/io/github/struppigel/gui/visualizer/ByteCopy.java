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
package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.visualizer.engine.ByteSource;
import io.github.struppigel.tools.visualizer.engine.ByteSourceClosedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.UncheckedIOException;
import java.util.concurrent.ExecutionException;
import java.util.function.IntPredicate;

/**
 * Copies all selected bytes as text in the format of a row of the bytes tab. The rows show only the first bytes, the
 * copy has all of them, as far as the memory allows.
 */
final class ByteCopy {

    private static final Logger LOGGER = LogManager.getLogger();
    /** more bytes are not copied, the text would be too long for the programs it is pasted into */
    static final long MAX_BYTES = 64L * 1024 * 1024;
    /**
     * the memory a character of the text needs while it is copied: 2 bytes in the builder, which switches to UTF-16
     * for the first character that is no Latin-1, 2 in the string made of it, and about 8 for the clipboard, which
     * converts the text into several native formats
     */
    private static final long MEMORY_PER_CHAR = 12;
    /** the bytes that are read at once; even, so that no UTF-16 character is split between two reads */
    static final int CHUNK = 64 * 1024;

    private ByteCopy() {}

    /**
     * The formats of the rows of the bytes tab.
     */
    enum Format {
        HEX("hex"), ASCII("ASCII"), UTF16("UTF-16");

        private final String title;

        Format(String title) {
            this.title = title;
        }

        String getTitle() {
            return title;
        }

        /**
         * @param label the label of the row
         * @return the format of the rows with the label, hex for rows that show no bytes as text
         */
        static Format ofLabel(String label) {
            if (ByteConversions.ASCII_LABEL.equals(label)) {
                return ASCII;
            }
            if (ByteConversions.UTF16_LABEL.equals(label)) {
                return UTF16;
            }
            return HEX;
        }

        /**
         * @return the characters of the text of the bytes
         */
        long chars(long bytes) {
            switch (this) {
                case HEX:
                    return bytes > 0 ? bytes * 3 - 1 : 0;
                case ASCII:
                    return bytes;
                default:
                    return bytes / 2;
            }
        }
    }

    /**
     * @param size            the number of bytes to copy
     * @param availableMemory the bytes of memory that can still be allocated
     * @return why the bytes are not copied, null if they can be
     */
    static String refusal(long size, Format format, long availableMemory) {
        if (size > MAX_BYTES) {
            return "Mark at most " + ByteConversions.size(MAX_BYTES) + " to copy";
        }
        if (format.chars(size) * MEMORY_PER_CHAR > availableMemory) {
            return notEnoughMemory(size, format);
        }
        return null;
    }

    /**
     * @return why the bytes are not copied with the memory available now, null if they can be
     */
    static String refusal(long size, Format format) {
        Runtime runtime = Runtime.getRuntime();
        return refusal(size, format, runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory()));
    }

    private static String notEnoughMemory(long size, Format format) {
        return "Not enough memory to copy " + ByteConversions.size(size) + " as " + format.getTitle();
    }

    /**
     * @param start       the offset of the first byte
     * @param end         the offset after the last byte
     * @param displayable tells whether the font of the bytes tab has a glyph for a character, see
     *                    {@link ByteConversions#rows(byte[], long, IntPredicate)}
     * @return the bytes as text, the same as the row of the format copies for the bytes it shows
     * @throws IllegalArgumentException if there are more than {@link #MAX_BYTES}
     */
    static String text(ByteSource source, long start, long end, Format format, IntPredicate displayable) {
        long size = Math.max(0, end - start);
        if (size > MAX_BYTES) {
            throw new IllegalArgumentException("too many bytes to copy: " + size);
        }
        StringBuilder out = new StringBuilder((int) format.chars(size));
        byte[] chunk = new byte[(int) Math.min(CHUNK, Math.max(1, size))];
        long at = start;
        while (at < end) {
            int read = source.read(at, chunk, 0, (int) Math.min(chunk.length, end - at));
            if (read <= 0) {
                break;
            }
            switch (format) {
                case HEX:
                    if (out.length() > 0) {
                        out.append(' ');
                    }
                    ByteConversions.appendHex(out, chunk, 0, read);
                    break;
                case ASCII:
                    ByteConversions.appendAscii(out, chunk, read);
                    break;
                default:
                    ByteConversions.appendUtf16(out, chunk, read, displayable);
            }
            at += read;
        }
        return out.toString();
    }

    /**
     * Puts the bytes as text on the clipboard without blocking the user interface. Tells the user if that fails.
     *
     * @param parent the component that a message about a failure is shown on
     */
    static void copyInBackground(Component parent, ByteSource source, long start, long end, Format format,
                                 IntPredicate displayable) {
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                // the memory may be taken since the menu was opened
                String refusal = refusal(end - start, format);
                if (refusal != null) {
                    return refusal;
                }
                try {
                    String text = text(source, start, end, format, displayable);
                    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
                    return null;
                } catch (OutOfMemoryError e) {
                    LOGGER.warn("Not enough memory to copy " + (end - start) + " bytes as " + format.getTitle());
                    return notEnoughMemory(end - start, format);
                } catch (ByteSourceClosedException e) {
                    return null; // another file is being loaded
                } catch (UncheckedIOException e) {
                    LOGGER.warn("Could not read the bytes to copy at " + start + ": " + e.getMessage());
                    return "Could not read the bytes: " + e.getMessage();
                } catch (IllegalStateException e) {
                    LOGGER.warn("Could not copy the bytes: " + e.getMessage());
                    return "The clipboard is not available, another program may be using it.";
                }
            }

            @Override
            protected void done() {
                try {
                    String problem = get();
                    if (problem != null) {
                        JOptionPane.showMessageDialog(parent, problem, "Copy", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.warn("Could not copy the bytes: " + e.getMessage());
                }
            }
        }.execute();
    }
}
