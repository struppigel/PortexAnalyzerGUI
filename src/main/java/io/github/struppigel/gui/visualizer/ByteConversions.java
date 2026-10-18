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

import io.github.struppigel.parser.sections.debug.CodeviewInfo;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * The values of selected bytes as rows of the info grid: numbers for up to 8 bytes, little and big endian, floating
 * point numbers and times for 4 and 8 bytes, a GUID for 16 bytes, hex of the first bytes, and ASCII and UTF-16 text
 * of more bytes.
 */
final class ByteConversions {

    /** the bytes that hex shows at most */
    static final int HEX_BYTES = 16;
    /** the bytes that ASCII and UTF-16 show at most */
    static final int TEXT_BYTES = 256;
    /** the bytes that the rows show at most, read from the start of the selection */
    static final int SHOWN_BYTES = Math.max(HEX_BYTES, TEXT_BYTES);
    /** the labels of the rows that show the bytes, which tell in what format all selected bytes are copied */
    static final String HEX_LABEL = "Hex";
    static final String ASCII_LABEL = "ASCII";
    static final String UTF16_LABEL = "UTF-16";
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();
    private static final int HEX_BYTES_PER_ROW = 8;
    /** the characters of a row of text, about as many as fit into the side panel */
    private static final int TEXT_CHARS_PER_ROW = 24;
    /** seconds from the start of FILETIME, 1601-01-01, to the start of Unix time */
    private static final long FILETIME_EPOCH_SECONDS = 11644473600L;
    private static final int MAX_YEAR = 9999;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private ByteConversions() {}

    private static final String[] SIZE_UNITS = {"KiB", "MiB", "GiB", "TiB"};

    /**
     * @return the number of bytes for people, e.g., "1 byte", "512 bytes", "20 KiB" or "1.5 MiB"
     */
    static String size(long bytes) {
        if (bytes < 1024) {
            return bytes + (bytes == 1 ? " byte" : " bytes");
        }
        double value = bytes;
        int unit = -1;
        while (value >= 1024 && unit < SIZE_UNITS.length - 1) {
            value /= 1024;
            unit++;
        }
        String number = value >= 100 || value == Math.floor(value) ? String.format(java.util.Locale.ROOT, "%.0f", value)
                : String.format(java.util.Locale.ROOT, "%.1f", value);
        return number + " " + SIZE_UNITS[unit];
    }

    /**
     * Like {@link #rows(byte[], long, IntPredicate)} for a font that displays every character.
     */
    static List<String[]> rows(byte[] firstBytes, long size) {
        return rows(firstBytes, size, c -> true);
    }

    /**
     * @param firstBytes  the first bytes of the selection, at most {@link #SHOWN_BYTES}, fewer only if the selection
     *                    is smaller
     * @param size        the number of selected bytes
     * @param displayable tells whether the font of the values has a glyph for a character; the UTF-16 text shows
     *                    the others as dots
     * @return label, value, the marker of the hex font, and for rows that show a part of a value what copying copies,
     *         see {@link InfoGrid#setRows}; an empty label continues the row above
     */
    static List<String[]> rows(byte[] firstBytes, long size, IntPredicate displayable) {
        List<String[]> rows = new ArrayList<>();
        byte[] bytes = Arrays.copyOf(firstBytes, (int) Math.min(firstBytes.length, Math.min(size, SHOWN_BYTES)));
        if (size == bytes.length && size >= 1 && size <= 8) {
            addNumbers(rows, bytes);
        }
        if (size == 4 && bytes.length == 4) {
            rows.add(mono("LE float", String.valueOf(Float.intBitsToFloat((int) littleEndian(bytes)))));
            rows.add(mono("BE float", String.valueOf(Float.intBitsToFloat((int) bigEndian(bytes)))));
            rows.add(mono("Unix time", time(Instant.ofEpochSecond(littleEndian(bytes)))));
        }
        if (size == 8 && bytes.length == 8) {
            rows.add(mono("LE double", String.valueOf(Double.longBitsToDouble(littleEndian(bytes)))));
            rows.add(mono("BE double", String.valueOf(Double.longBitsToDouble(bigEndian(bytes)))));
            rows.add(mono("FILETIME", fileTime(littleEndian(bytes))));
        }
        if (size == 16 && bytes.length == 16) {
            // in two rows, the GUID is too wide for the side panel
            String guid = CodeviewInfo.guidToString(bytes).toUpperCase();
            int split = guid.indexOf('-', guid.indexOf('-', guid.indexOf('-') + 1) + 1) + 1;
            rows.add(mono("GUID", guid.substring(0, split), guid));
            rows.add(mono("", guid.substring(split), guid));
        }
        if (bytes.length > 0) {
            addHex(rows, Arrays.copyOf(bytes, Math.min(bytes.length, HEX_BYTES)));
            byte[] text = Arrays.copyOf(bytes, Math.min(bytes.length, TEXT_BYTES));
            addText(rows, ASCII_LABEL, ascii(text));
            addText(rows, UTF16_LABEL, utf16(text, displayable));
        }
        return rows;
    }

    private static void addNumbers(List<String[]> rows, byte[] bytes) {
        if (bytes.length == 1) {
            addNumber(rows, "Value", "Signed", littleEndian(bytes), 1);
            return;
        }
        addNumber(rows, "LE", "LE signed", littleEndian(bytes), bytes.length);
        addNumber(rows, "BE", "BE signed", bigEndian(bytes), bytes.length);
    }

    /**
     * Adds the value unsigned, in hex, and signed if that is another number.
     */
    private static void addNumber(List<String[]> rows, String label, String signedLabel, long value, int size) {
        rows.add(mono(label, Long.toUnsignedString(value)));
        rows.add(mono("", String.format("0x%0" + size * 2 + "X", value)));
        long signed = signExtend(value, size);
        if (signed < 0) {
            rows.add(mono(signedLabel, String.valueOf(signed)));
        }
    }

    private static long signExtend(long value, int size) {
        int shift = 64 - size * 8;
        return value << shift >> shift;
    }

    private static long littleEndian(byte[] bytes) {
        long value = 0;
        for (int i = bytes.length - 1; i >= 0; i--) {
            value = value << 8 | (bytes[i] & 0xff);
        }
        return value;
    }

    private static long bigEndian(byte[] bytes) {
        long value = 0;
        for (byte b : bytes) {
            value = value << 8 | (b & 0xff);
        }
        return value;
    }

    /**
     * @param value the 100 nanosecond intervals since 1601-01-01, unsigned
     */
    private static String fileTime(long value) {
        // unsigned values above Long.MAX_VALUE are far beyond the year 9999
        if (value < 0) {
            return "out of range";
        }
        Instant instant = Instant.ofEpochSecond(value / 10_000_000 - FILETIME_EPOCH_SECONDS,
                value % 10_000_000 * 100);
        if (instant.atZone(ZoneOffset.UTC).getYear() > MAX_YEAR) {
            return "out of range";
        }
        return time(instant);
    }

    private static String time(Instant instant) {
        return TIME_FORMAT.format(instant);
    }

    /**
     * Adds the bytes in rows of {@link #HEX_BYTES_PER_ROW}; every row copies all bytes.
     */
    private static void addHex(List<String[]> rows, byte[] bytes) {
        StringBuilder all = new StringBuilder();
        List<String> rowTexts = new ArrayList<>();
        for (int from = 0; from < bytes.length; from += HEX_BYTES_PER_ROW) {
            StringBuilder hex = new StringBuilder();
            appendHex(hex, bytes, from, Math.min(bytes.length, from + HEX_BYTES_PER_ROW));
            rowTexts.add(hex.toString());
            all.append(all.length() > 0 ? " " : "").append(hex);
        }
        for (int i = 0; i < rowTexts.size(); i++) {
            rows.add(mono(i == 0 ? HEX_LABEL : "", rowTexts.get(i), all.toString()));
        }
    }

    /**
     * Appends the bytes as uppercase hex digits separated by spaces, like the hex rows show them.
     */
    static void appendHex(StringBuilder out, byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (i > from) {
                out.append(' ');
            }
            out.append(HEX_DIGITS[(bytes[i] >> 4) & 0xf]).append(HEX_DIGITS[bytes[i] & 0xf]);
        }
    }

    /**
     * Adds the text in rows of {@link #TEXT_CHARS_PER_ROW} characters, the label on the first; every row copies the
     * whole text.
     */
    private static void addText(List<String[]> rows, String label, String text) {
        if (text.isEmpty()) {
            rows.add(mono(label, ""));
            return;
        }
        for (int from = 0; from < text.length(); from += TEXT_CHARS_PER_ROW) {
            String row = text.substring(from, Math.min(text.length(), from + TEXT_CHARS_PER_ROW));
            rows.add(mono(from == 0 ? label : "", row, text));
        }
    }

    private static String ascii(byte[] bytes) {
        StringBuilder text = new StringBuilder();
        appendAscii(text, bytes, bytes.length);
        return text.toString();
    }

    /**
     * Appends the first bytes as ASCII, bytes that are no printable characters as dots.
     */
    static void appendAscii(StringBuilder out, byte[] bytes, int length) {
        for (int i = 0; i < length; i++) {
            int value = bytes[i] & 0xff;
            out.append(value >= 0x20 && value < 0x7f ? (char) value : '.');
        }
    }

    private static String utf16(byte[] bytes, IntPredicate displayable) {
        StringBuilder text = new StringBuilder();
        appendUtf16(text, bytes, bytes.length, displayable);
        return text.toString();
    }

    /**
     * Appends the first bytes as UTF-16 little endian; a trailing odd byte is left out, characters that cannot be
     * shown, that the font has no glyph for or that change the text direction are dots.
     */
    static void appendUtf16(StringBuilder out, byte[] bytes, int length, IntPredicate displayable) {
        for (int i = 0; i + 1 < length; i += 2) {
            char c = (char) ((bytes[i] & 0xff) | (bytes[i + 1] & 0xff) << 8);
            out.append(isShown(c) && displayable.test(c) ? c : '.');
        }
    }

    private static boolean isShown(char c) {
        switch (Character.getType(c)) {
            case Character.CONTROL:
            case Character.FORMAT:
            case Character.SURROGATE:
            case Character.PRIVATE_USE:
            case Character.UNASSIGNED:
            case Character.LINE_SEPARATOR:
            case Character.PARAGRAPH_SEPARATOR:
                return false;
            default:
                return true;
        }
    }

    private static String[] mono(String label, String value) {
        return new String[]{label, value, InfoGrid.MONO};
    }

    /**
     * @param copied what copying the row copies, e.g., the whole text of which the row shows a part
     */
    private static String[] mono(String label, String value, String copied) {
        return new String[]{label, value, InfoGrid.MONO, copied};
    }
}
