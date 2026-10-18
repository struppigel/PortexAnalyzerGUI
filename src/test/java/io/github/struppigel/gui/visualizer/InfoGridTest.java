package io.github.struppigel.gui.visualizer;

import com.formdev.flatlaf.FlatLaf;
import io.github.struppigel.gui.Starter;
import io.github.struppigel.testsupport.Swing;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static org.junit.jupiter.api.Assertions.*;

class InfoGridTest {

    private static List<String> texts(JPopupMenu menu) {
        List<String> texts = new ArrayList<>();
        for (MenuElement element : menu.getSubElements()) {
            texts.add(((JMenuItem) element).getText());
        }
        return texts;
    }

    @Test
    void aRowCopiesItsValueOrTheWholeValueItShowsAPartOf() throws Exception {
        onEdt(() -> {
            InfoGrid grid = new InfoGrid();
            String text = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

            assertEquals(List.of("Copy 590156", "Copy all"),
                    texts(grid.createMenu(new String[]{"LE", "590156", InfoGrid.MONO})));
            assertEquals(List.of("Copy ABCDEFGHIJKLMNOPQRSTUVWX...", "Copy all"),
                    texts(grid.createMenu(new String[]{"", "YZ", InfoGrid.MONO, text})),
                    "the whole text, shortened in the menu");
            assertEquals(text, InfoGrid.copiedText(new String[]{"", "YZ", InfoGrid.MONO, text}));
        });
    }

    @Test
    void headingsHintsAndTheRoomBesideTheRowsCopyOnlyAllRows() throws Exception {
        onEdt(() -> {
            InfoGrid grid = new InfoGrid();

            assertEquals(List.of("Copy all"), texts(grid.createMenu(new String[]{null, "Selected bytes",
                    InfoGrid.TITLE})));
            assertEquals(List.of("Copy all"), texts(grid.createMenu(new String[]{null, "Drag to mark bytes"})));
            assertEquals(List.of("Copy all"), texts(grid.createMenu(null)));
            assertEquals(List.of("Copy all"), texts(grid.createMenu(new String[]{"ASCII", ""})), "nothing to copy");
        });
    }

    private static final String HEX = "4C 01 09 00";

    /**
     * A heading, a number in two rows that are two values, hex in two rows that are one value, an empty value and a
     * hint.
     */
    private static List<String[]> rows(String hint) {
        return List.of(
                new String[]{null, "Selected bytes", InfoGrid.TITLE},
                new String[]{"LE", "590156", InfoGrid.MONO},
                new String[]{"", "0x0009014C", InfoGrid.MONO},
                new String[]{"Hex", "4C 01", InfoGrid.MONO, HEX},
                new String[]{"", "09 00", InfoGrid.MONO, HEX},
                new String[]{"ASCII", "", InfoGrid.MONO},
                new String[]{null, hint});
    }

    private static InfoGrid grid() {
        InfoGrid grid = new InfoGrid();
        grid.setRows(rows("Drag to mark bytes"));
        return grid;
    }

    private static void click(JComponent component) {
        component.dispatchEvent(new MouseEvent(component, MouseEvent.MOUSE_CLICKED, 0, 0, 2, 2, 1, false,
                MouseEvent.BUTTON1));
    }

    private static List<Integer> marked(InfoGrid grid) {
        List<Integer> marked = new ArrayList<>();
        for (int i = 0; i < grid.getRows().size(); i++) {
            if (grid.isMarked(i)) {
                marked.add(i);
            }
        }
        return marked;
    }

    /**
     * @return the label in the left column with the text
     */
    private static JLabel label(InfoGrid grid, String text) {
        List<JLabel> values = new ArrayList<>();
        for (int i = 0; i < grid.getRows().size(); i++) {
            values.add(grid.getValueLabel(i));
        }
        for (JLabel label : Swing.findAll(grid, JLabel.class)) {
            if (text.equals(label.getText()) && !values.contains(label)) {
                return label;
            }
        }
        throw new AssertionError("no label " + text);
    }

    @Test
    void aClickMarksTheValueOfTheRowAndOnlyIt() throws Exception {
        onEdt(() -> {
            InfoGrid grid = grid();
            assertEquals(List.of(), marked(grid));

            click(grid.getValueLabel(1));
            assertEquals(List.of(1), marked(grid));
            click(grid.getValueLabel(2));
            assertEquals(List.of(2), marked(grid), "the hex of a number is a value of its own");
            click(label(grid, "LE"));
            assertEquals(List.of(1), marked(grid), "a click on the label marks its value");
        });
    }

    @Test
    void aValueInSeveralRowsIsMarkedAsAWhole() throws Exception {
        onEdt(() -> {
            InfoGrid grid = grid();

            click(grid.getValueLabel(4));
            assertEquals(List.of(3, 4), marked(grid));
            click(label(grid, "Hex"));
            assertEquals(List.of(3, 4), marked(grid));
            assertArrayEquals(new int[]{3, 4}, grid.valueRows(3));
            assertArrayEquals(new int[]{2, 2}, grid.valueRows(2));
        });
    }

    @Test
    void headingsHintsAndEmptyValuesAreNotMarked() throws Exception {
        onEdt(() -> {
            InfoGrid grid = grid();

            click(grid.getValueLabel(0));
            click(grid.getValueLabel(5));
            click(grid.getValueLabel(6));
            click(grid);
            assertEquals(List.of(), marked(grid));

            click(grid.getValueLabel(1));
            click(grid.getValueLabel(0));
            click(grid);
            assertEquals(List.of(1), marked(grid), "the mark stays");
        });
    }

    @Test
    void theMarkStaysWhileItsRowsStay() throws Exception {
        onEdt(() -> {
            InfoGrid grid = grid();
            click(grid.getValueLabel(3));

            grid.setRows(rows("Ctrl + G goes to an offset"));
            assertEquals(List.of(3, 4), marked(grid), "other rows changed");
            List<String[]> changed = new ArrayList<>(rows("Ctrl + G goes to an offset"));
            changed.set(4, new String[]{"", "09 01", InfoGrid.MONO, "4C 01 09 01"});
            grid.setRows(changed);
            assertEquals(List.of(), marked(grid), "the value changed");
            grid.setRows(rows("Ctrl + G goes to an offset"));
            assertEquals(List.of(), marked(grid));
        });
    }

    @Test
    void theRowsOfTheGridOfferWhatTheGridCopiesInsteadOfAllRows() throws Exception {
        onEdt(() -> {
            InfoGrid grid = grid();
            grid.setCopyAllItem(label -> label == null || label.equals("ASCII") ? null
                    : new JMenuItem("All as " + label));
            List<String[]> rows = grid.getRows();

            assertEquals(List.of("Copy " + HEX, "All as Hex"), texts(grid.createMenu(rows.get(4))),
                    "the label of the first row of the value");
            assertEquals(List.of("Copy 590156", "All as LE"), texts(grid.createMenu(rows.get(1))));
            assertEquals(List.of(), texts(grid.createMenu(rows.get(5))), "no item for this value");
            assertEquals(List.of(), texts(grid.createMenu(null)));
        });
    }

    @Test
    void valuesFromTheFileAreNotRenderedAsHtml() throws Exception {
        onEdt(() -> {
            InfoGrid grid = new InfoGrid();
            grid.setRows(List.<String[]>of(new String[]{"ASCII", "<html><b>MZ</b>", InfoGrid.MONO}));

            assertNull(grid.getValueLabel(0).getClientProperty(javax.swing.plaf.basic.BasicHTML.propertyKey));
        });
    }

    @Test
    void aMarkedValueIsPaintedInTheSelectionColorsOfThePortexTheme() throws Exception {
        LookAndFeel previous = UIManager.getLookAndFeel();
        try {
            onEdt(() -> {
                Starter.setDarkLookAndFeel();
                InfoGrid grid = grid();
                JLabel value = grid.getValueLabel(1);
                click(value);
                value.setSize(120, value.getPreferredSize().height);

                BufferedImage image = new BufferedImage(value.getWidth(), value.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                Graphics g = image.getGraphics();
                value.paint(g);
                g.dispose();

                assertEquals(0xAA0000, image.getRGB(value.getWidth() - 1, 0) & 0xffffff, "the red of the selection");
                assertEquals(UIManager.getColor("List.selectionForeground"), value.getForeground());
            });
        } finally {
            onEdt(() -> {
                FlatLaf.setGlobalExtraDefaults(null);
                UIManager.setLookAndFeel(previous);
            });
        }
    }
}
