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
package io.github.struppigel.gui.utils;

import io.github.struppigel.gui.visualizer.Spacing;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Information as headings, label and value rows, bullet lists, stat tiles and colored badges, instead of plain
 * text. Add the content, then call {@link #render()}. Everything that is added is escaped, so text from the analyzed
 * file is never rendered as HTML.
 */
public class InfoCard extends JEditorPane {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int MAX_KEY_LENGTH = 40;
    // a mouse that moved less than this between pressing and releasing clicked
    private static final int CLICK_DISTANCE = 4;
    // the share of the text color in the color of labels, all labels look the same
    private static final double LABEL_WEIGHT = 0.7;

    /** what a badge says about its value */
    public enum Kind {
        NEUTRAL(new Color(0x546E7A)), GOOD(new Color(0x2E7D32)), WARN(new Color(0xB26A00)), BAD(new Color(0xC62828));

        private final Color color;

        Kind(Color color) {
            this.color = color;
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private final StringBuilder plain = new StringBuilder();
    private Highlighter.HighlightPainter hoverPainter =
            new DefaultHighlighter.DefaultHighlightPainter(new Color(128, 128, 128, 60));
    private Object hoverTag;
    private int[] hoverRange;

    public InfoCard() {
        setContentType("text/html");
        setEditable(false);
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        setBorder(Spacing.padding());
        addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getURL() != null) {
                open(e.getURL().toString());
            }
        });
        ToolTipManager.sharedInstance().registerComponent(this);
        MouseAdapter copySupport = new MouseAdapter() {
            private Point pressed;

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = e.getPoint();
                showPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                showPopup(e);
                // a click, not the end of a drag that selects some text
                if (SwingUtilities.isLeftMouseButton(e) && !e.isPopupTrigger() && pressed != null
                        && pressed.distance(e.getPoint()) < CLICK_DISTANCE) {
                    selectValueAt(offsetAt(e.getPoint()));
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                clearHover();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearHover();
            }
        };
        addMouseListener(copySupport);
        addMouseMotionListener(copySupport);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        return valueRangeAt(event.getPoint()) == null ? null : "Click to select the value, right-click to copy";
    }

    /**
     * @return the position in the text at the point, -1 if the point is not at a line of text
     */
    private int offsetAt(Point point) {
        int offset = viewToModel(point);
        try {
            Rectangle line = offset < 0 ? null : modelToView(offset);
            return line == null || point.y < line.y || point.y > line.y + line.height ? -1 : offset;
        } catch (BadLocationException e) {
            return -1;
        }
    }

    /**
     * @return the range of the value at the point that a click selects, null for links, which a click opens, and
     * for anything that is no value
     */
    private int[] valueRangeAt(Point point) {
        int offset = offsetAt(point);
        return offset < 0 || isLinkAt(offset) ? null : valueRangeAtOffset(offset);
    }

    private boolean isLinkAt(int offset) {
        Document document = getDocument();
        return document instanceof HTMLDocument && ((HTMLDocument) document).getCharacterElement(offset)
                .getAttributes().getAttribute(HTML.Tag.A) != null;
    }

    /**
     * Shows with a highlight what a click would select.
     */
    private void updateHover(Point point) {
        int[] range = valueRangeAt(point);
        if (java.util.Arrays.equals(range, hoverRange)) {
            return;
        }
        clearHover();
        if (range != null) {
            try {
                hoverTag = getHighlighter().addHighlight(range[0], range[1], hoverPainter);
                hoverRange = range;
            } catch (BadLocationException e) {
                LOGGER.debug("No highlight: " + e.getMessage());
            }
        }
    }

    private void clearHover() {
        if (hoverTag != null) {
            getHighlighter().removeHighlight(hoverTag);
        }
        hoverTag = null;
        hoverRange = null;
    }

    private void showPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        String value = valueTextAtOffset(offsetAt(e.getPoint()));
        JPopupMenu menu = new JPopupMenu();
        JMenuItem copyValue = new JMenuItem("Copy value");
        copyValue.setEnabled(value != null);
        copyValue.addActionListener(a -> copy(value));
        JMenuItem copySelection = new JMenuItem("Copy selection");
        copySelection.setEnabled(getSelectedText() != null);
        copySelection.addActionListener(a -> copy(getSelectedText()));
        JMenuItem copyAll = new JMenuItem("Copy all");
        copyAll.addActionListener(a -> copy(getPlainText()));
        menu.add(copyValue);
        menu.add(copySelection);
        menu.add(copyAll);
        menu.show(this, e.getX(), e.getY());
    }

    private static void copy(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private void selectValueAt(int offset) {
        int[] range = offset < 0 || isLinkAt(offset) ? null : valueRangeAtOffset(offset);
        if (range != null) {
            requestFocusInWindow();
            select(range[0], range[1]);
        }
    }

    /**
     * @return the text of the value at the position in the text: the value of a row, a tile, a heading; null if
     * there is none, e.g., at an empty cell
     */
    String valueTextAtOffset(int offset) {
        int[] range = valueRangeAtOffset(offset);
        if (range == null) {
            return null;
        }
        try {
            return getDocument().getText(range[0], range[1] - range[0]);
        } catch (BadLocationException e) {
            return null;
        }
    }

    /**
     * @return start and end of the trimmed text of the value cell at the position, null if it is empty
     */
    private int[] valueRangeAtOffset(int offset) {
        Document document = getDocument();
        if (offset < 0 || !(document instanceof HTMLDocument)) {
            return null;
        }
        Element cell = ((HTMLDocument) document).getCharacterElement(offset);
        while (cell != null && !"td".equals(cell.getName())) {
            cell = cell.getParentElement();
        }
        if (cell == null) {
            return null;
        }
        Element row = cell.getParentElement();
        boolean tile = "center".equals(String.valueOf(cell.getAttributes().getAttribute(HTML.Attribute.ALIGN)));
        if (!tile && row != null && row.getElementCount() == 2 && row.getElement(0) == cell) {
            cell = row.getElement(1); // the value next to the label
        }
        try {
            int start = cell.getStartOffset();
            int end = Math.min(cell.getEndOffset(), document.getLength());
            String text = document.getText(start, end - start);
            int from = 0;
            int to = text.length();
            while (from < to && Character.isWhitespace(text.charAt(from))) {
                from++;
            }
            if (tile) {
                // the number, not the name below it
                to = from;
                while (to < text.length() && !Character.isWhitespace(text.charAt(to))) {
                    to++;
                }
            }
            while (to > from && Character.isWhitespace(text.charAt(to - 1))) {
                to--;
            }
            return from == to ? null : new int[]{start + from, start + to};
        } catch (BadLocationException e) {
            return null;
        }
    }

    private static void open(String url) {
        // only links the program added, and only to web pages
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ex) {
            LOGGER.warn("Could not open " + url + ": " + ex.getMessage());
        }
    }

    // --- content ---

    public InfoCard clear() {
        rows.clear();
        plain.setLength(0);
        return this;
    }

    public InfoCard heading(String text) {
        spaceAbove();
        rows.add(new Row(Row.Type.HEADING, text, null, null));
        line(text);
        return this;
    }

    /** a title of a list, e.g., "Characteristics:" */
    public InfoCard subheading(String text) {
        rows.add(new Row(Row.Type.SUBHEADING, text, null, null));
        line(text);
        return this;
    }

    public InfoCard row(String label, String value) {
        rows.add(new Row(Row.Type.ROW, label, value, null));
        line(label + ": " + value);
        return this;
    }

    /**
     * A row whose value starts with a colored badge: the text before the first ", " or " (" is the badge, the
     * rest follows as text.
     */
    public InfoCard badgeRow(String label, String value, Kind kind) {
        int comma = value.indexOf(", ");
        int paren = value.indexOf(" (");
        int cut = comma < 0 ? paren : (paren < 0 ? comma : Math.min(comma, paren));
        String badge = cut < 0 ? value : value.substring(0, cut);
        String rest = cut < 0 ? "" : value.substring(cut).replaceFirst("^,\\s*", "").trim();
        rows.add(new Row(Row.Type.ROW, label, rest, kind, badge, true, null, 0, null));
        line(label + ": " + value);
        return this;
    }

    /** a row whose value is followed by a colored badge */
    public InfoCard rowWithBadge(String label, String value, String badge, Kind kind) {
        rows.add(new Row(Row.Type.ROW, label, value, kind, badge, false, null, 0, null));
        line(label + ": " + value + " (" + badge + ")");
        return this;
    }

    public InfoCard bullet(String text) {
        rows.add(new Row(Row.Type.BULLET, null, text, null));
        line("  * " + text);
        return this;
    }

    public InfoCard paragraph(String text) {
        rows.add(new Row(Row.Type.PARAGRAPH, null, text, null));
        line(text);
        return this;
    }

    /** a bold line over the whole width, spaced from what is above, e.g., the title of a long text */
    public InfoCard lead(String text) {
        spaceAbove();
        rows.add(new Row(Row.Type.LEAD, null, text, null));
        line(text);
        return this;
    }

    /** a bullet over the whole width for long texts, that wrap under their first line */
    public InfoCard textBullet(String text) {
        rows.add(new Row(Row.Type.TEXT_BULLET, null, text, null));
        line("  * " + text);
        return this;
    }

    /** a link in a line of its own, to a web page */
    public InfoCard link(String label, String text, String url) {
        rows.add(new Row(Row.Type.LINK, label, text, null, url));
        line(label + ": " + text);
        return this;
    }

    /**
     * Big numbers with their names below, side by side.
     *
     * @param columns the tiles per line
     * @param tiles   alternating name and value
     */
    public InfoCard tiles(int columns, String... tiles) {
        for (int i = 0; i + 1 < tiles.length; i += 2) {
            line(tiles[i] + ": " + tiles[i + 1]);
        }
        rows.add(new Row(Row.Type.TILES, null, null, null, null, columns, tiles));
        return this;
    }

    /**
     * Adds lines of text: "name: value" becomes a row, "* text" a bullet, "name:" a subheading, empty lines space.
     */
    public InfoCard addText(String text) {
        for (String raw : text.split("\r?\n", -1)) {
            String line = raw.trim();
            if (isUnderline(line)) {
                continue; // underlines of titles in text reports
            }
            if (line.isEmpty()) {
                spaceAbove();
            } else if (line.startsWith("* ")) {
                bullet(line.substring(2).trim());
            } else if (line.endsWith(":")) {
                subheading(line);
            } else {
                int colon = line.indexOf(": ");
                if (colon > 0 && colon <= MAX_KEY_LENGTH && line.lastIndexOf(',', colon) < 0) {
                    row(line.substring(0, colon).trim(), line.substring(colon + 2).trim());
                } else {
                    paragraph(line);
                }
            }
        }
        return this;
    }

    /**
     * @return true for a line like "*****" that only underlines a title in the text reports made for the command
     * line
     */
    public static boolean isUnderline(String line) {
        return line.trim().matches("([*=_~-])\\1{2,}");
    }

    private void spaceAbove() {
        if (!rows.isEmpty() && rows.get(rows.size() - 1).type != Row.Type.GAP) {
            rows.add(new Row(Row.Type.GAP, null, null, null));
        }
    }

    private void line(String text) {
        if (plain.length() > 0) {
            plain.append('\n');
        }
        plain.append(text);
    }

    /**
     * @return the content as lines of text, labels followed by their values, for tests and copying
     */
    public String getPlainText() {
        return plain.toString();
    }

    /**
     * Shows what was added, in the colors of the current theme.
     */
    public InfoCard render() {
        Color background = color("Panel.background", Color.white);
        Color foreground = color("Label.foreground", Color.black);
        setBackground(background);
        setForeground(foreground);
        hoverPainter = new DefaultHighlighter.DefaultHighlightPainter(
                new Color(foreground.getRed(), foreground.getGreen(), foreground.getBlue(), 45));
        clearHover();
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            setFont(font);
        }
        String muted = hex(blend(foreground, background, LABEL_WEIGHT));
        String tile = hex(color("Table.background", background));
        int labelWidth = labelWidth();

        StringBuilder html = new StringBuilder("<html><body><table width=\"100%\" cellspacing=\"0\" cellpadding=\"3\">");
        for (Row row : rows) {
            row.appendTo(html, muted, tile, labelWidth, background);
        }
        html.append("</table></body></html>");
        setText(html.toString());
        setCaretPosition(0);
        revalidate();
        repaint();
        return this;
    }

    private int labelWidth() {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = 60;
        for (Row row : rows) {
            if (row.type == Row.Type.ROW || row.type == Row.Type.LINK || row.type == Row.Type.SUBHEADING) {
                width = Math.max(width, metrics.stringWidth(row.labelText()));
            }
        }
        return width + Spacing.PAD * 2;
    }

    private static Color color(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private static String hex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * @param weight the share of the first color, 1 is the first color
     */
    private static Color blend(Color first, Color second, double weight) {
        return new Color((int) Math.round(first.getRed() * weight + second.getRed() * (1 - weight)),
                (int) Math.round(first.getGreen() * weight + second.getGreen() * (1 - weight)),
                (int) Math.round(first.getBlue() * weight + second.getBlue() * (1 - weight)));
    }

    static String escape(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&':
                    out.append("&amp;");
                    break;
                case '<':
                    out.append("&lt;");
                    break;
                case '>':
                    out.append("&gt;");
                    break;
                case '"':
                    out.append("&quot;");
                    break;
                case '\n':
                    out.append("<br>");
                    break;
                case '\t':
                    out.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                    break;
                default:
                    // control characters have no place in the page
                    if (c >= ' ') {
                        out.append(c);
                    }
            }
        }
        return out.toString();
    }

    private static final class Row {

        enum Type {HEADING, SUBHEADING, ROW, LINK, BULLET, TEXT_BULLET, PARAGRAPH, LEAD, TILES, GAP}

        final Type type;
        final String label;
        final String value;
        final Kind badge;
        final String badgeText;
        final boolean badgeFirst;
        final String url;
        final int columns;
        final String[] tiles;

        Row(Type type, String label, String value, Kind badge) {
            this(type, label, value, badge, null, false, null, 0, null);
        }

        Row(Type type, String label, String value, Kind badge, String url) {
            this(type, label, value, badge, null, false, url, 0, null);
        }

        Row(Type type, String label, String value, Kind badge, String url, int columns, String[] tiles) {
            this(type, label, value, badge, null, false, url, columns, tiles);
        }

        Row(Type type, String label, String value, Kind badge, String badgeText, boolean badgeFirst, String url,
            int columns, String[] tiles) {
            this.type = type;
            this.label = label;
            this.value = value;
            this.badge = badge;
            this.badgeText = badgeText;
            this.badgeFirst = badgeFirst;
            this.url = url;
            this.columns = columns;
            this.tiles = tiles;
        }

        /**
         * @return the label without the colon that text reports put after it
         */
        String labelText() {
            String text = label.trim();
            return text.endsWith(":") ? text.substring(0, text.length() - 1).trim() : text;
        }

        void appendTo(StringBuilder html, String muted, String tileColor, int labelWidth, Color background) {
            switch (type) {
                case GAP:
                    html.append("<tr><td colspan=\"2\" height=\"6\"></td></tr>");
                    break;
                case HEADING:
                    html.append("<tr><td colspan=\"2\"><b>").append(escape(label)).append("</b><hr></td></tr>");
                    break;
                case SUBHEADING:
                    // a label like those of the rows, its list follows in the value column
                    html.append("<tr><td valign=\"top\" width=\"").append(labelWidth).append("\"><font color=\"")
                            .append(muted).append("\">").append(escape(labelText())).append("</font></td><td></td></tr>");
                    break;
                case PARAGRAPH:
                    html.append("<tr><td colspan=\"2\">").append(escape(value)).append("</td></tr>");
                    break;
                case BULLET:
                    html.append("<tr><td width=\"").append(labelWidth).append("\"></td><td valign=\"top\">&#8226;&nbsp;")
                            .append(escape(value)).append("</td></tr>");
                    break;
                case LEAD:
                    html.append("<tr><td colspan=\"2\"><b>").append(escape(value)).append("</b></td></tr>");
                    break;
                case TEXT_BULLET:
                    // the text wraps under its first line, not under the bullet
                    html.append("<tr><td colspan=\"2\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\"><tr>"
                            + "<td width=\"20\" valign=\"top\" align=\"right\">&#8226;&nbsp;</td><td valign=\"top\">")
                            .append(escape(value)).append("</td></tr></table></td></tr>");
                    break;
                case ROW:
                    html.append("<tr><td valign=\"top\" width=\"").append(labelWidth).append("\"><font color=\"")
                            .append(muted).append("\">").append(escape(label)).append("</font></td><td valign=\"top\">");
                    if (badge == null) {
                        html.append(escape(value));
                    } else {
                        appendWithBadge(html, background);
                    }
                    html.append("</td></tr>");
                    break;
                case LINK:
                    html.append("<tr><td valign=\"top\" width=\"").append(labelWidth).append("\"><font color=\"")
                            .append(muted).append("\">").append(escape(label)).append("</font></td><td valign=\"top\">")
                            .append("<a href=\"").append(escape(url)).append("\">").append(escape(value))
                            .append("</a></td></tr>");
                    break;
                case TILES:
                    appendTiles(html, muted, tileColor);
                    break;
                default:
                    break;
            }
        }

        private void appendTiles(StringBuilder html, String muted, String tileColor) {
            int width = 100 / columns;
            html.append("<tr><td colspan=\"2\"><table width=\"100%\" cellspacing=\"6\" cellpadding=\"10\">");
            int count = tiles.length / 2;
            for (int i = 0; i < count; i++) {
                if (i % columns == 0) {
                    html.append(i == 0 ? "<tr>" : "</tr><tr>");
                }
                html.append("<td align=\"center\" width=\"").append(width).append("%\" bgcolor=\"").append(tileColor)
                        .append("\"><font size=\"6\"><b>").append(escape(tiles[2 * i + 1])).append("</b></font><br><font color=\"")
                        .append(muted).append("\">").append(escape(tiles[2 * i])).append("</font></td>");
            }
            html.append("</tr></table></td></tr>");
        }

        /**
         * The badge is a padded box in a tint of its color with text in a lighter or darker shade of it, so that it
         * fits the theme.
         */
        private void appendWithBadge(StringBuilder html, Color background) {
            boolean dark = (0.299 * background.getRed() + 0.587 * background.getGreen()
                    + 0.114 * background.getBlue()) < 128;
            Color tint = blend(badge.color, background, dark ? 0.35 : 0.15);
            Color text = dark ? blend(badge.color, Color.white, 0.4) : blend(badge.color, Color.black, 0.8);
            String pill = "<table cellspacing=\"0\" cellpadding=\"2\"><tr><td bgcolor=\"" + hex(tint)
                    + "\"><font color=\"" + hex(text) + "\">&nbsp;" + escape(badgeText) + "&nbsp;</font></td></tr></table>";
            html.append("<table cellspacing=\"0\" cellpadding=\"0\"><tr>");
            if (badgeFirst) {
                html.append("<td valign=\"middle\">").append(pill).append("</td>");
                if (!value.isEmpty()) {
                    html.append("<td valign=\"middle\">&nbsp;&nbsp;").append(escape(value)).append("</td>");
                }
            } else {
                html.append("<td valign=\"middle\">").append(escape(value)).append("&nbsp;&nbsp;</td><td valign=\"middle\">")
                        .append(pill).append("</td>");
            }
            html.append("</tr></table>");
        }
    }
}
