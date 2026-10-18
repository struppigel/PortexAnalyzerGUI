package io.github.struppigel.gui;

import io.github.struppigel.tools.layout.StructureMap;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.io.File;
import java.util.Collections;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The main frame needs a screen, so only its parts that do not are tested.
 */
class MainFrameTest {

    @Test
    void parseNoticeShowsWhyTheFileIsShownWithoutPEStructures() throws Exception {
        JLabel notice = new JLabel();
        FileData broken = data("could not read the section table");
        FileData clean = data(null);

        onEdt(() -> MainFrame.updateParseNotice(notice, broken));
        assertTrue(notice.isVisible());
        assertTrue(notice.getToolTipText().contains("could not read the section table"), notice.getToolTipText());

        onEdt(() -> MainFrame.updateParseNotice(notice, clean));
        assertFalse(notice.isVisible());
        assertNull(notice.getToolTipText());
    }

    private static FileData data(String peParseError) {
        return new FileData(new File("sample.bin"), "", Collections.emptyList(), StructureMap.empty(0), peParseError);
    }
}
