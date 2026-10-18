package io.github.struppigel.gui.visualizer;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ByteConversionsTest {

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    private static List<String[]> rows(byte[] bytes) {
        return ByteConversions.rows(bytes, bytes.length);
    }

    /**
     * @return the value of each labeled row, continuation rows appended to the row above with " | "
     */
    private static Map<String, String> values(List<String[]> rows) {
        Map<String, String> values = new LinkedHashMap<>();
        String last = null;
        for (String[] row : rows) {
            assertEquals(InfoGrid.MONO, row[2], "in the hex font: " + row[0]);
            if (row[0].isEmpty()) {
                values.put(last, values.get(last) + " | " + row[1]);
            } else {
                last = row[0];
                values.put(last, row[1]);
            }
        }
        return values;
    }

    @Test
    void fourBytesAreNumbersFloatsAndAUnixTime() {
        Map<String, String> values = values(rows(bytes(0x4C, 0x01, 0x09, 0x00)));

        assertEquals("590156 | 0x0009014C", values.get("LE"));
        assertEquals("1275136256 | 0x4C010900", values.get("BE"));
        assertFalse(values.containsKey("LE signed"), "the same as unsigned");
        assertFalse(values.containsKey("BE signed"));
        assertEquals(String.valueOf(Float.intBitsToFloat(0x0009014C)), values.get("LE float"));
        assertEquals(String.valueOf(Float.intBitsToFloat(0x4C010900)), values.get("BE float"));
        assertEquals("1970-01-07 19:55:56 UTC", values.get("Unix time"));
        assertEquals("4C 01 09 00", values.get("Hex"));
        assertEquals("L...", values.get("ASCII"));
        assertFalse(values.containsKey("GUID"));
        assertFalse(values.containsKey("LE double"));
    }

    @Test
    void oneByteIsOneValueAndSignedIfNegative() {
        Map<String, String> values = values(rows(bytes(0xFF)));

        assertEquals("255 | 0xFF", values.get("Value"));
        assertEquals("-1", values.get("Signed"));
        assertFalse(values.containsKey("LE"), "one byte has no byte order");
        assertEquals("", values.get("UTF-16"), "an odd byte is left out");

        assertFalse(values(rows(bytes(0x41))).containsKey("Signed"));
    }

    @Test
    void eightBytesAreUnsignedAndSignedNumbersAndAFileTime() {
        Map<String, String> values = values(rows(bytes(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF)));

        assertEquals("18446744073709551615 | 0xFFFFFFFFFFFFFFFF", values.get("LE"));
        assertEquals("-1", values.get("LE signed"));
        assertEquals("-1", values.get("BE signed"));
        assertEquals("NaN", values.get("LE double"));
        assertEquals("out of range", values.get("FILETIME"));
        assertEquals("FF FF FF FF FF FF FF FF", values.get("Hex"));
    }

    @Test
    void fileTimeCountsFrom1601() {
        // 116444736000000000 = 0x019DB1DED53E8000, the start of Unix time
        Map<String, String> values = values(rows(bytes(0x00, 0x80, 0x3E, 0xD5, 0xDE, 0xB1, 0x9D, 0x01)));

        assertEquals("1970-01-01 00:00:00 UTC", values.get("FILETIME"));
        assertEquals("116444736000000000 | 0x019DB1DED53E8000", values.get("LE"));
    }

    @Test
    void sixteenBytesAreAGuid() {
        Map<String, String> values = values(rows(bytes(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09,
                0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F)));

        assertEquals("03020100-0504-0706- | 0809-0A0B0C0D0E0F", values.get("GUID"), "in two rows");
        assertFalse(values.containsKey("LE"), "too large for a number");
        assertEquals("00 01 02 03 04 05 06 07 | 08 09 0A 0B 0C 0D 0E 0F", values.get("Hex"));
    }

    @Test
    void anySizeUpToEightBytesIsANumber() {
        Map<String, String> values = values(rows(bytes(0x01, 0x02, 0x03)));

        assertEquals("197121 | 0x030201", values.get("LE"));
        assertEquals("66051 | 0x010203", values.get("BE"));
        assertFalse(values.containsKey("LE float"));
        assertFalse(values.containsKey("Unix time"));
    }

    @Test
    void largeSelectionsShowTheFirstBytesInHexAndMoreAsText() {
        byte[] first = new byte[ByteConversions.SHOWN_BYTES];
        for (int i = 0; i < first.length; i++) {
            first[i] = (byte) ('A' + i % 26);
        }

        Map<String, String> values = values(ByteConversions.rows(first, 300));

        assertFalse(values.containsKey("LE"));
        assertFalse(values.containsKey("GUID"), "only exactly 16 bytes");
        assertEquals("41 42 43 44 45 46 47 48 | 49 4A 4B 4C 4D 4E 4F 50", values.get("Hex"), "16 bytes");
        String[] ascii = values.get("ASCII").split(" \\| ");
        assertEquals("ABCDEFGHIJKLMNOPQRSTUVWX", ascii[0], "rows of 24 characters");
        assertEquals(ByteConversions.TEXT_BYTES, String.join("", ascii).length());
        assertEquals((ByteConversions.TEXT_BYTES + 23) / 24, ascii.length);
        String[] utf16 = values.get("UTF-16").split(" \\| ");
        assertEquals(ByteConversions.TEXT_BYTES / 2, String.join("", utf16).length(), "two bytes per character");
        assertEquals(24, utf16[0].length());
    }

    @Test
    void rowsThatShowAPartOfAValueCopyTheWholeValue() {
        byte[] first = new byte[ByteConversions.SHOWN_BYTES];
        for (int i = 0; i < first.length; i++) {
            first[i] = (byte) ('a' + i % 26);
        }
        String ascii = new String(first, java.nio.charset.StandardCharsets.US_ASCII);
        boolean inAscii = false;
        int asciiRows = 0;
        for (String[] row : ByteConversions.rows(first, first.length)) {
            inAscii = row[0].equals("ASCII") || inAscii && row[0].isEmpty();
            if (inAscii) {
                asciiRows++;
                assertEquals(ascii, InfoGrid.copiedText(row), "every row of the text copies all of it");
            }
            if (row[0].equals("Hex")) {
                assertEquals("61 62 63 64 65 66 67 68 69 6A 6B 6C 6D 6E 6F 70", InfoGrid.copiedText(row));
            }
        }
        assertTrue(asciiRows > 1);

        byte[] guid = new byte[16];
        for (String[] row : ByteConversions.rows(guid, 16)) {
            if (row[1].contains("-0000") || row[0].equals("GUID")) {
                assertEquals("00000000-0000-0000-0000-000000000000", InfoGrid.copiedText(row), "both GUID rows");
            }
        }
        List<String[]> number = ByteConversions.rows(bytes(0x4C, 0x01, 0x09, 0x00), 4);
        assertEquals("590156", InfoGrid.copiedText(number.get(0)), "a number copies itself");
        assertEquals("0x0009014C", InfoGrid.copiedText(number.get(1)));
    }

    @Test
    void theTextIsAsLongAsTheReadBytes() {
        byte[] first = "ABCDEFGHIJKLMNOP".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        Map<String, String> values = values(ByteConversions.rows(first, 20));

        assertEquals("ABCDEFGHIJKLMNOP", values.get("ASCII"));
        assertEquals(8, values.get("UTF-16").length(), values.get("UTF-16"));
    }

    @Test
    void textShowsDotsForBytesThatCannotBeShown() {
        Map<String, String> values = values(rows(bytes('H', 0, 'i', 0, 0x2E, 0x20, 0x0A, 0x00, 0x00)));

        // 0x2E is a dot itself, 0x20 a space
        assertEquals("H.i.. ...", values.get("ASCII"));
        // U+202E would turn the text around, a line feed would break the row
        assertEquals("Hi..", values.get("UTF-16"));
    }

    @Test
    void charactersWithoutGlyphInTheFontAreDots() {
        byte[] first = new byte[16];
        // 'A', then U+31AD, a character that fonts without CJK do not have
        first[0] = 'A';
        first[2] = (byte) 0xAD;
        first[3] = 0x31;

        Map<String, String> values = values(ByteConversions.rows(first, 20, c -> c < 0x80));

        assertTrue(values.get("UTF-16").startsWith("A......."), values.get("UTF-16"));
    }

    @Test
    void noBytesNoRows() {
        assertTrue(ByteConversions.rows(new byte[0], 0).isEmpty());
        List<String> labels = new ArrayList<>();
        for (String[] row : rows(bytes(0x10, 0x20))) {
            labels.add(row[0]);
        }
        assertEquals(List.of("LE", "", "BE", "", "Hex", "ASCII", "UTF-16"), labels);
    }

    @Test
    void sizesForPeople() {
        assertEquals("1 byte", ByteConversions.size(1));
        assertEquals("1023 bytes", ByteConversions.size(1023));
        assertEquals("20 KiB", ByteConversions.size(20 * 1024));
        assertEquals("1.5 MiB", ByteConversions.size(3 * 512 * 1024));
        assertEquals("100 MiB", ByteConversions.size(100L * 1024 * 1024 + 1));
        assertEquals("2 GiB", ByteConversions.size(2L * 1024 * 1024 * 1024));
    }
}
