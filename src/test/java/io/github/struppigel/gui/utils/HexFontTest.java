package io.github.struppigel.gui.utils;

import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class HexFontTest {

    @Test
    void prefersFontsMadeForCode() {
        assertEquals("Consolas", HexFont.pick(new HashSet<>(Arrays.asList("Courier New", "Menlo", "Consolas"))));
        assertEquals("Menlo", HexFont.pick(new HashSet<>(Arrays.asList("Courier New", "Menlo"))));
        assertEquals("DejaVu Sans Mono", HexFont.pick(new HashSet<>(Arrays.asList("DejaVu Sans Mono", "Arial"))));
    }

    @Test
    void fallsBackToJavasMonospaced() {
        assertEquals(Font.MONOSPACED, HexFont.pick(Collections.singleton("Courier New")));
    }

    @Test
    void usesAnInstalledFont() {
        String family = HexFont.family();
        assertTrue(HexFont.PREFERRED.contains(family) || Font.MONOSPACED.equals(family), family);
        // Java substitutes another font for unknown names, the family shows that the font is really used
        assertEquals(family, HexFont.font(Font.PLAIN, 13).getFamily());
    }
}
