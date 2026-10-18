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

import javax.swing.*;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.Locale;

/**
 * Asks for bytes to mark: the start and either the end, which is included, or the length, in hex or decimal.
 */
final class RangeDialog {

    private static final String TITLE = "Select range";
    private static final int FIELD_COLUMNS = 16;

    private RangeDialog() {}

    /**
     * Asks until the user enters a range of the file or cancels.
     *
     * @param parent     the component that the dialog is shown on
     * @param initial    start and exclusive end of the bytes that the fields show first
     * @param hex        true if the numbers are hex at first
     * @param fileLength the number of bytes of the file
     * @return start and exclusive end of the entered range, null if the user cancelled
     */
    static long[] ask(Component parent, long[] initial, boolean hex, long fileLength) {
        JTextField startField = new JTextField(format(initial[0], hex), FIELD_COLUMNS);
        JTextField secondField = new JTextField(format(initial[1] - 1, hex), FIELD_COLUMNS);
        JRadioButton endButton = new JRadioButton("End, included", true);
        JRadioButton lengthButton = new JRadioButton("Length");
        JRadioButton hexButton = new JRadioButton("Hex", hex);
        JRadioButton decButton = new JRadioButton("Dec", !hex);
        group(endButton, lengthButton);
        group(hexButton, decButton);

        // switching converts the numbers in the fields, so that they stay the same bytes
        boolean[] shownHex = {hex};
        ActionListener radix = a -> {
            boolean newHex = hexButton.isSelected();
            if (newHex != shownHex[0]) {
                convertRadix(startField, shownHex[0], newHex);
                convertRadix(secondField, shownHex[0], newHex);
                shownHex[0] = newHex;
            }
        };
        hexButton.addActionListener(radix);
        decButton.addActionListener(radix);
        boolean[] shownLength = {false};
        ActionListener kind = a -> {
            boolean newLength = lengthButton.isSelected();
            if (newLength != shownLength[0]) {
                boolean isHex = hexButton.isSelected();
                long start = parseNumber(startField.getText(), isHex);
                long value = parseNumber(secondField.getText(), isHex);
                if (start >= 0 && value >= 0) {
                    long converted = newLength ? value - start + 1 : start + value - 1;
                    if (converted >= 0) {
                        secondField.setText(format(converted, isHex));
                    }
                }
                shownLength[0] = newLength;
            }
        };
        endButton.addActionListener(kind);
        lengthButton.addActionListener(kind);
        // the start can be typed right away
        startField.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                SwingUtilities.invokeLater(() -> {
                    startField.requestFocusInWindow();
                    startField.selectAll();
                });
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
            }
        });

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, Spacing.PAD);
        c.gridy = 0;
        panel.add(new JLabel("Start"), c);
        panel.add(startField, c);
        c.gridy++;
        panel.add(row(endButton, lengthButton), c);
        panel.add(secondField, c);
        c.gridy++;
        panel.add(new JLabel("Numbers"), c);
        panel.add(row(hexButton, decButton), c);
        c.gridy++;
        c.gridwidth = 2;
        JLabel hint = new JLabel("A number with 0x is always hex.");
        hint.setForeground(UIManager.getColor("Label.disabledForeground"));
        panel.add(hint, c);

        while (true) {
            int answer = JOptionPane.showConfirmDialog(parent, panel, TITLE, JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (answer != JOptionPane.OK_OPTION) {
                return null;
            }
            try {
                return parseRange(startField.getText(), secondField.getText(), lengthButton.isSelected(),
                        hexButton.isSelected(), fileLength);
            } catch (IllegalArgumentException e) {
                JOptionPane.showMessageDialog(parent, e.getMessage(), TITLE, JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private static void group(AbstractButton... buttons) {
        ButtonGroup group = new ButtonGroup();
        for (AbstractButton button : buttons) {
            group.add(button);
        }
    }

    private static JPanel row(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
        for (JComponent component : components) {
            row.add(component);
        }
        return row;
    }

    private static void convertRadix(JTextField field, boolean fromHex, boolean toHex) {
        long value = parseNumber(field.getText(), fromHex);
        if (value >= 0) {
            field.setText(format(value, toHex));
        }
    }

    static String format(long value, boolean hex) {
        return hex ? "0x" + Long.toHexString(value) : String.valueOf(value);
    }

    /**
     * @param hex true if a number without 0x prefix is hex
     * @return the number, hex if it has the prefix 0x; -1 if it is none, negative or too large
     */
    static long parseNumber(String text, boolean hex) {
        String s = text.trim().toLowerCase(Locale.ROOT).replace("_", "");
        boolean isHex = hex;
        if (s.startsWith("0x")) {
            s = s.substring(2);
            isHex = true;
        }
        if (!s.matches(isHex ? "[0-9a-f]+" : "[0-9]+")) {
            return -1;
        }
        try {
            return Long.parseLong(s, isHex ? 16 : 10);
        } catch (NumberFormatException e) {
            return -1; // too large
        }
    }

    /**
     * @param secondIsLength true if the second number is the length, otherwise it is the end, which is included
     * @param hex            true if numbers without 0x prefix are hex
     * @return start and exclusive end of the range
     * @throws IllegalArgumentException with a message for the user if the range is not in the file
     */
    static long[] parseRange(String startText, String secondText, boolean secondIsLength, boolean hex,
                             long fileLength) {
        String radix = hex ? "hex" : "decimal";
        long start = parseNumber(startText, hex);
        if (start < 0) {
            throw new IllegalArgumentException("The start is no " + radix + " number: " + startText.trim());
        }
        if (start >= fileLength) {
            throw new IllegalArgumentException("The start is after the end of the file at "
                    + format(fileLength - 1, hex));
        }
        String what = secondIsLength ? "length" : "end";
        long second = parseNumber(secondText, hex);
        if (second < 0) {
            throw new IllegalArgumentException("The " + what + " is no " + radix + " number: " + secondText.trim());
        }
        if (secondIsLength) {
            if (second == 0) {
                throw new IllegalArgumentException("The length must be at least 1.");
            }
            if (second > fileLength - start) {
                throw new IllegalArgumentException("The range goes past the end of the file at "
                        + format(fileLength - 1, hex));
            }
            return new long[]{start, start + second};
        }
        if (second < start) {
            throw new IllegalArgumentException("The end is before the start.");
        }
        if (second >= fileLength) {
            throw new IllegalArgumentException("The end is after the end of the file at "
                    + format(fileLength - 1, hex));
        }
        return new long[]{start, second + 1};
    }
}
