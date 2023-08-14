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
package com.github.struppigel.gui;

import javax.swing.*;

/**
 * Small window showing the stuff in the About section
 */
public class AboutFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    public static String version = "0.12.11";
    private static final String text = "Portex Analyzer GUI" + "\n\n" + "Version: " + version
            + "\nAuthor: Karsten Hahn"
            + "\nLast update: 14. August 2023"
            + "\n\nI develop this software as a hobby in my free time."
            + "\n\nIf you like it, please consider buying me a coffee: https://ko-fi.com/struppigel"
            + "\n\nThe repository is available at https://github.com/struppigel/PortexAnalyzerGUI";

    public AboutFrame() {
        super("About PortexAnalyzer GUI");
        initGUI();
    }

    private void initGUI() {
        this.setSize(350, 320);
        this.setResizable(false);
        this.setVisible(true);
        this.setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(text);
        area.setEditable(false);
        this.add(area);
    }
}