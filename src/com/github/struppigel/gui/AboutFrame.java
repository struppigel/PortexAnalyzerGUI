/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

public class AboutFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    public static String version = "0.11.2";
    private static final String text = "Portex Analyzer GUI" + "\n\n" + "Version: " + version
            + "\n\nAuthor: Karsten Hahn"
            + "\n\nLast update: 17. December 2022"
            + "\n\nIf you like this software, donate a coffee: https://ko-fi.com/struppigel";

    public AboutFrame() {
        initGUI();
    }

    private void initGUI() {
        this.setSize(350, 250);
        this.setResizable(false);
        this.setVisible(true);
        setLocationRelativeTo(null);
        this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTextArea area = new JTextArea();
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setText(text);
        area.setEditable(false);
        this.add(area);
    }
}