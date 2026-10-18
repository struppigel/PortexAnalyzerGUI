package io.github.struppigel.gui.visualizer;

import io.github.struppigel.tools.visualizer.engine.ByteSource;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ByteCopyTest {

    private static final long MIB = 1024 * 1024;

    private static ByteSource source(byte[] data) {
        return new ByteSource() {
            @Override
            public long length() {
                return data.length;
            }

            @Override
            public int byteAt(long offset) {
                return offset >= 0 && offset < data.length ? data[(int) offset] & 0xff : -1;
            }

            @Override
            public int read(long offset, byte[] buffer, int bufferOffset, int length) {
                if (offset >= data.length) {
                    return 0;
                }
                int count = (int) Math.min(length, data.length - offset);
                System.arraycopy(data, (int) offset, buffer, bufferOffset, count);
                return count;
            }
        };
    }

    private static byte[] pattern(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (i * 7);
        }
        return data;
    }

    private static String copiedText(List<String[]> rows, String label) {
        for (String[] row : rows) {
            if (label.equals(row[0])) {
                return InfoGrid.copiedText(row);
            }
        }
        throw new AssertionError("no row " + label);
    }

    @Test
    void theTextOfFewBytesIsWhatTheirRowsCopy() {
        byte[] data = "xxMZ\u0090\u0000Hello, world\r\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        ByteSource source = source(data);
        byte[] selected = Arrays.copyOfRange(data, 2, 18);
        List<String[]> rows = ByteConversions.rows(selected, selected.length);

        assertEquals(copiedText(rows, ByteConversions.HEX_LABEL),
                ByteCopy.text(source, 2, 18, ByteCopy.Format.HEX, c -> true));
        assertEquals(copiedText(rows, ByteConversions.ASCII_LABEL),
                ByteCopy.text(source, 2, 18, ByteCopy.Format.ASCII, c -> true));
        assertEquals(copiedText(rows, ByteConversions.UTF16_LABEL),
                ByteCopy.text(source, 2, 18, ByteCopy.Format.UTF16, c -> true));
        assertEquals("4D 5A 90 00", ByteCopy.text(source, 2, 6, ByteCopy.Format.HEX, c -> true));
        assertEquals("", ByteCopy.text(source, 2, 2, ByteCopy.Format.HEX, c -> true), "no bytes");
    }

    @Test
    void allBytesAreCopiedAlsoOverSeveralReads() {
        byte[] data = pattern(ByteCopy.CHUNK * 2 + 4);
        ByteSource source = source(data);
        // from an odd offset, so that the reads do not start at even offsets of the file
        byte[] selected = Arrays.copyOfRange(data, 1, data.length);

        StringBuilder hex = new StringBuilder();
        for (byte b : selected) {
            hex.append(hex.length() > 0 ? " " : "").append(String.format("%02X", b & 0xff));
        }
        assertEquals(hex.toString(), ByteCopy.text(source, 1, data.length, ByteCopy.Format.HEX, c -> true));
        StringBuilder ascii = new StringBuilder();
        ByteConversions.appendAscii(ascii, selected, selected.length);
        assertEquals(ascii.toString(), ByteCopy.text(source, 1, data.length, ByteCopy.Format.ASCII, c -> true));
        StringBuilder utf16 = new StringBuilder();
        ByteConversions.appendUtf16(utf16, selected, selected.length, c -> c < 0x3000);
        String copied = ByteCopy.text(source, 1, data.length, ByteCopy.Format.UTF16, c -> c < 0x3000);
        assertEquals(utf16.toString(), copied);
        assertEquals(selected.length / 2, copied.length(), "the odd last byte is left out");
    }

    @Test
    void theFormatOfARowFollowsItsLabel() {
        assertEquals(ByteCopy.Format.ASCII, ByteCopy.Format.ofLabel(ByteConversions.ASCII_LABEL));
        assertEquals(ByteCopy.Format.UTF16, ByteCopy.Format.ofLabel(ByteConversions.UTF16_LABEL));
        assertEquals(ByteCopy.Format.HEX, ByteCopy.Format.ofLabel(ByteConversions.HEX_LABEL));
        assertEquals(ByteCopy.Format.HEX, ByteCopy.Format.ofLabel("LE"), "values that are no text");
        assertEquals(ByteCopy.Format.HEX, ByteCopy.Format.ofLabel(null), "beside the rows");
    }

    @Test
    void tooManyBytesOrTooLittleMemoryAreRefused() {
        assertNull(ByteCopy.refusal(ByteCopy.MAX_BYTES, ByteCopy.Format.HEX, Long.MAX_VALUE));
        assertEquals("Mark at most 64 MiB to copy",
                ByteCopy.refusal(ByteCopy.MAX_BYTES + 1, ByteCopy.Format.ASCII, Long.MAX_VALUE));
        assertEquals("Not enough memory to copy 1 MiB as hex",
                ByteCopy.refusal(MIB, ByteCopy.Format.HEX, 10 * MIB));
        assertNull(ByteCopy.refusal(MIB, ByteCopy.Format.UTF16, 10 * MIB), "half as many characters as bytes");
        assertNull(ByteCopy.refusal(0, ByteCopy.Format.HEX, 0));
        assertThrows(IllegalArgumentException.class, () -> ByteCopy.text(source(new byte[4]), 0,
                ByteCopy.MAX_BYTES + 1, ByteCopy.Format.HEX, c -> true));
    }
}
