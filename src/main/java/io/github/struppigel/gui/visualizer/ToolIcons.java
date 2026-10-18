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
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;

/**
 * The icons of the toolbar. They are drawn in the foreground color of the button, so they fit any theme.
 */
final class ToolIcons {

    private static final int SIZE = 18;

    private ToolIcons() {
    }

    static Icon zoomIn() {
        return new GlyphIcon(Glyph.MAGNIFIER, true);
    }

    static Icon zoomOut() {
        return new GlyphIcon(Glyph.MAGNIFIER, false);
    }

    static Icon wholeFile() {
        return new GlyphIcon(Glyph.FRAME, false);
    }

    static Icon goTo() {
        return new GlyphIcon(Glyph.GOTO, false);
    }

    private enum Glyph {
        MAGNIFIER, FRAME, GOTO
    }

    private static final class GlyphIcon implements Icon {
        private final Glyph glyph;
        private final boolean plus;

        GlyphIcon(Glyph glyph, boolean plus) {
            this.glyph = glyph;
            this.plus = plus;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g2.setColor(c.isEnabled() ? c.getForeground() : UIManager.getColor("Label.disabledForeground"));
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.translate(x, y);
                switch (glyph) {
                    case MAGNIFIER:
                        paintMagnifier(g2);
                        break;
                    case FRAME:
                        paintFrame(g2);
                        break;
                    default:
                        paintGoTo(g2);
                        break;
                }
            } finally {
                g2.dispose();
            }
        }

        private void paintMagnifier(Graphics2D g2) {
            g2.draw(new Ellipse2D.Double(2, 2, 10, 10));
            g2.draw(new Line2D.Double(11, 11, 16, 16));
            g2.draw(new Line2D.Double(4.5, 7, 9.5, 7));
            if (plus) {
                g2.draw(new Line2D.Double(7, 4.5, 7, 9.5));
            }
        }

        // four corners, the file fills the view
        private void paintFrame(Graphics2D g2) {
            int a = 2;
            int b = SIZE - 3;
            int len = 5;
            g2.draw(new Line2D.Double(a, a + len, a, a));
            g2.draw(new Line2D.Double(a, a, a + len, a));
            g2.draw(new Line2D.Double(b - len, a, b, a));
            g2.draw(new Line2D.Double(b, a, b, a + len));
            g2.draw(new Line2D.Double(a, b - len, a, b));
            g2.draw(new Line2D.Double(a, b, a + len, b));
            g2.draw(new Line2D.Double(b - len, b, b, b));
            g2.draw(new Line2D.Double(b, b - len, b, b));
        }

        // an arrow towards a bar
        private void paintGoTo(Graphics2D g2) {
            g2.draw(new Line2D.Double(2, 9, 12, 9));
            g2.draw(new Line2D.Double(8, 5, 12, 9));
            g2.draw(new Line2D.Double(8, 13, 12, 9));
            g2.draw(new Line2D.Double(15, 3, 15, 15));
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }
}
