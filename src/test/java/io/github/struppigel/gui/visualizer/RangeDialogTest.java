package io.github.struppigel.gui.visualizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RangeDialogTest {

    private static final long FILE = 0x1000;

    private static String refusal(String start, String second, boolean secondIsLength, boolean hex) {
        return assertThrows(IllegalArgumentException.class,
                () -> RangeDialog.parseRange(start, second, secondIsLength, hex, FILE)).getMessage();
    }

    @Test
    void numbersAreHexOrDecimalAndAlwaysHexWith0x() {
        assertEquals(0x400, RangeDialog.parseNumber("400", true));
        assertEquals(400, RangeDialog.parseNumber("400", false));
        assertEquals(0x400, RangeDialog.parseNumber(" 0x400 ", false), "0x is always hex");
        assertEquals(0x1a40, RangeDialog.parseNumber("0X1A40", false));
        assertEquals(0x1000, RangeDialog.parseNumber("1_000", true));
        assertEquals(-1, RangeDialog.parseNumber("1a", false), "letters are no decimal digits");
        assertEquals(-1, RangeDialog.parseNumber("-5", false));
        assertEquals(-1, RangeDialog.parseNumber("+5", false));
        assertEquals(-1, RangeDialog.parseNumber("0x-5", false));
        assertEquals(-1, RangeDialog.parseNumber("", true));
        assertEquals(-1, RangeDialog.parseNumber("0x", true));
        assertEquals(-1, RangeDialog.parseNumber("10000000000000000", true), "too large");
    }

    @Test
    void numbersAreShownInTheChosenFormat() {
        assertEquals("0x5ff", RangeDialog.format(0x5ff, true));
        assertEquals("1535", RangeDialog.format(0x5ff, false));
    }

    @Test
    void theRangeEndsWithTheEndOrAfterTheLength() {
        assertArrayEquals(new long[]{0x400, 0x600}, RangeDialog.parseRange("0x400", "0x5ff", false, true, FILE),
                "the end is included");
        assertArrayEquals(new long[]{0x400, 0x600}, RangeDialog.parseRange("400", "200", true, true, FILE));
        assertArrayEquals(new long[]{1024, 1536}, RangeDialog.parseRange("1024", "1535", false, false, FILE));
        assertArrayEquals(new long[]{1024, 1536}, RangeDialog.parseRange("1024", "512", true, false, FILE));
        assertArrayEquals(new long[]{5, 6}, RangeDialog.parseRange("5", "5", false, false, FILE), "one byte");
        assertArrayEquals(new long[]{0, FILE}, RangeDialog.parseRange("0", "0x1000", true, false, FILE),
                "the whole file");
    }

    @Test
    void rangesThatAreNotInTheFileAreExplained() {
        assertEquals("The start is no decimal number: abc", refusal("abc", "1", true, false));
        assertEquals("The length is no hex number: 1g", refusal("0", "1g", true, true));
        assertEquals("The start is after the end of the file at 0xfff", refusal("0x1000", "1", true, true));
        assertEquals("The end is before the start.", refusal("0x10", "0xf", false, true));
        assertEquals("The end is after the end of the file at 4095", refusal("0", "4096", false, false));
        assertEquals("The length must be at least 1.", refusal("0", "0", true, false));
        assertEquals("The range goes past the end of the file at 0xfff", refusal("0xff0", "0x11", true, true));
        assertEquals("The range goes past the end of the file at 0xfff",
                refusal("1", "0x7fffffffffffffff", true, true), "no overflow");
    }
}
