package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.gui.PEFieldsTable;
import io.github.struppigel.gui.pedetails.FileContentPreviewPanel;
import io.github.struppigel.settings.PortexSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.github.struppigel.testsupport.Swing.*;
import static org.junit.jupiter.api.Assertions.*;

class SignaturesPanelTest {

    @TempDir
    Path tempDir;

    @Test
    void showsNoMatchesRow() throws Exception {
        SignaturesPanel panel = panel();

        onEdt(() -> panel.buildPEiDTables(new ArrayList<>()));

        JTable summary = table(panel, "Source");
        assertEquals(1, summary.getRowCount());
        assertEquals("no matches found", summary.getValueAt(0, 0));
    }

    @Test
    void selectingNoMatchesRowDoesNotFail() throws Exception {
        SignaturesPanel panel = panel();
        onEdt(() -> panel.buildPEiDTables(new ArrayList<>()));
        JTable patterns = table(panel, "Match name");

        onEdt(() -> patterns.setRowSelectionInterval(0, 0));

        assertEquals(0, patterns.getSelectedRow());
    }

    @Test
    void selectingRuleFiltersPatterns() throws Exception {
        SignaturesPanel panel = panel();
        List<PEidRuleMatch> matches = new ArrayList<>();
        matches.add(new PEidRuleMatch("First", "Entry Point",
                Collections.singletonList(new PatternMatch(1, "", "41")), "PEiD"));
        matches.add(new PEidRuleMatch("Second", "Overlay",
                Collections.singletonList(new PatternMatch(2, "", "42")), "Filetype"));
        onEdt(() -> panel.buildPEiDTables(matches));
        JTable summary = table(panel, "Source");
        JTable patterns = table(panel, "Match name");
        assertEquals(2, patterns.getRowCount());

        onEdt(() -> summary.setRowSelectionInterval(1, 1));

        assertEquals(1, patterns.getRowCount());
        assertEquals("Second", patterns.getValueAt(0, 0));
    }

    private SignaturesPanel panel() throws Exception {
        PortexSettings settings = new PortexSettings(tempDir.resolve("settings.ini").toFile());
        return onEdtGet(() -> new SignaturesPanel(settings, new FileContentPreviewPanel()));
    }

    /**
     * @return the table whose first column has the given name
     */
    private static JTable table(SignaturesPanel panel, String firstColumn) throws Exception {
        return onEdtGet(() -> findAll(panel, PEFieldsTable.class).stream()
                .filter(t -> t.getColumnName(0).equals(firstColumn))
                .findFirst().orElseThrow(() -> new AssertionError("no table with column " + firstColumn)));
    }
}
