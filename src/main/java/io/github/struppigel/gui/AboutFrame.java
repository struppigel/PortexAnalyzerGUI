/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
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
package io.github.struppigel.gui;

import io.github.struppigel.gui.utils.AppIcon;
import io.github.struppigel.gui.utils.InfoCard;
import io.github.struppigel.gui.visualizer.Spacing;

import javax.swing.*;
import java.awt.*;

/**
 * Small window showing the stuff in the About section
 */
public class AboutFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    public static String version = "1.0.0";
    private static final String COFFEE_URL = "https://ko-fi.com/struppigel";
    private static final String REPOSITORY_URL = "https://github.com/struppigel/PortexAnalyzerGUI";

    public AboutFrame() {
        super("About PortexAnalyzer GUI");
        initGUI();
    }

    private void initGUI() {
        this.setSize(380, 560);
        this.setResizable(false);
        AppIcon.applyTo(this);
        this.setVisible(true);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        InfoCard card = new InfoCard();
        card.heading("Portex Analyzer GUI")
                .row("Version", version)
                .row("Author", "Karsten Hahn")
                .row("Last update", "11. October 2026")
                .paragraph("I develop this software as a hobby in my free time.")
                .paragraph("If you like it, please consider buying me a coffee.")
                .link("Ko-fi", "ko-fi.com/struppigel", COFFEE_URL)
                .link("Repository", "github.com/struppigel/PortexAnalyzerGUI", REPOSITORY_URL)
                .render();
        // flat, in the color of the text, like the rest of the page
        Color text = UIManager.getColor("Label.foreground");
        JLabel logo = new JLabel(AppIcon.logo(256, text == null ? Color.gray : text));
        logo.setBorder(Spacing.padding(Spacing.PAD * 2, 0, 0, 0));
        this.setLayout(new BorderLayout());
        this.add(logo, BorderLayout.NORTH);
        this.add(card, BorderLayout.CENTER);
    }
}