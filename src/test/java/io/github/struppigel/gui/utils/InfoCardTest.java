package io.github.struppigel.gui.utils;

import org.junit.jupiter.api.Test;

import javax.swing.text.Document;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static org.junit.jupiter.api.Assertions.*;

class InfoCardTest {

    @Test
    void rowsBulletsAndTilesHavePlainText() throws Exception {
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.heading("Overview").row("Machine type", "x86")
                    .badgeRow("Authenticode", "VALID", InfoCard.Kind.GOOD)
                    .tiles(3, "Sections", "5", "Imports", "12")
                    .subheading("Characteristics:").bullet("DLL").paragraph("done").render();

            assertEquals("Overview\nMachine type: x86\nAuthenticode: VALID\nSections: 5\nImports: 12\n"
                    + "Characteristics:\n  * DLL\ndone", card.getPlainText());
        });
    }

    @Test
    void linesOfTextBecomeRowsBulletsAndHeadings() throws Exception {
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.addText("Time date stamp : today\nMachine type: Intel\nCharacteristics: \n\t* executable\n"
                    + "\t*  second\n\nEntry point is in section 1\n").render();

            assertEquals("Time date stamp: today\nMachine type: Intel\nCharacteristics:\n  * executable\n"
                    + "  * second\nEntry point is in section 1\n", card.getPlainText() + "\n");
        });
    }

    @Test
    void textFromTheFileIsNeverShownAsHtml() throws Exception {
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.row("Name", "<b>bold</b> <img src=\"http://example.com/x.png\">").bullet("<html>x").render();

            Document document = card.getDocument();
            String shown = document.getText(0, document.getLength());
            assertTrue(shown.contains("<b>bold</b> <img src=\"http://example.com/x.png\">"), shown);
            assertTrue(shown.contains("<html>x"), shown);
        });
    }

    @Test
    void escapesControlCharactersAndMarkup() {
        assertEquals("a&lt;b&gt;&amp;&quot;<br>c", InfoCard.escape("a<b>&\"\nc\u0000"));
    }

    @Test
    void badgesKeepTheTextOfTheRow() throws Exception {
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.badgeRow("Authenticode", "VALID, signed by Microsoft", InfoCard.Kind.GOOD)
                    .badgeRow("Integrity", "INVALID (the signature can not be parsed)", InfoCard.Kind.BAD)
                    .rowWithBadge("Entropy", "7.91", "packed", InfoCard.Kind.WARN).render();

            assertEquals("Authenticode: VALID, signed by Microsoft\n"
                    + "Integrity: INVALID (the signature can not be parsed)\nEntropy: 7.91 (packed)",
                    card.getPlainText());
            Document document = card.getDocument();
            String shown = document.getText(0, document.getLength());
            assertTrue(shown.contains("VALID") && shown.contains("signed by Microsoft"), shown);
            assertTrue(shown.contains("packed"), shown);
        });
    }

    @Test
    void underlinesOfTextReportsAreSkipped() throws Exception {
        assertTrue(InfoCard.isUnderline("*************************"));
        assertTrue(InfoCard.isUnderline(" ----- "));
        assertFalse(InfoCard.isUnderline("* item"));
        assertFalse(InfoCard.isUnderline("**"));
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.addText("Title\n*****\nKey: value\n");
            assertEquals("Title\nKey: value", card.getPlainText());
        });
    }

    @Test
    void valueOfARowIsFoundForCopying() throws Exception {
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.row("Machine type", "Intel 386").tiles(2, "Sections", "5", "Imports", "12").render();
            Document document = card.getDocument();
            String shown = document.getText(0, document.getLength());

            assertEquals("Intel 386", card.valueTextAtOffset(shown.indexOf("Machine")));
            assertEquals("Intel 386", card.valueTextAtOffset(shown.indexOf("Intel") + 2));
            assertEquals("12", card.valueTextAtOffset(shown.indexOf("Imports")));
        });
    }

    @Test
    void longTextsRunOverTheWholeWidth() throws Exception {
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.row("Total hints", "2").lead("Looks packed").textBullet("a long description of a finding").render();

            assertEquals("Total hints: 2\nLooks packed\n  * a long description of a finding", card.getPlainText());
        });
    }

    @Test
    void clearRemovesTheContent() throws Exception {
        onEdt(() -> {
            InfoCard card = new InfoCard();
            card.row("a", "b").render();
            card.clear().render();
            assertEquals("", card.getPlainText());
        });
    }
}
