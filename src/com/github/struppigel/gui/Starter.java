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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.*;

/**
 * Sets the look and feel and starts the GUI
 */
public class Starter {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args) {
        LOGGER.debug("starting program");
        setLookAndFeel2();
        initMainFrame();
    }

    private static void initMainFrame() {
        SwingUtilities.invokeLater(() -> new MainFrame());
    }

    private static void setLookAndFeel() {
        try {
            // Set System L&F
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
        }
        catch (ClassNotFoundException | InstantiationException
               | IllegalAccessException
               | UnsupportedLookAndFeelException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private static void setLookAndFeel2() {
        UIManager.put("nimbusBase", new Color(15, 0, 0));
        UIManager.put("nimbusBlueGrey", new Color(170, 0, 0));
        UIManager.put("control", Color.black);

        UIManager.put("text", Color.white);

        UIManager.put("nimbusSelectionBackground", Color.gray);
        UIManager.put("nimbusSelectedText", Color.white);
        UIManager.put("textHighlight", Color.lightGray);
        UIManager.put("nimbusFocus", new Color(0xBA3A1E));
        UIManager.put("nimbusSelection", new Color(170, 0, 0));
        UIManager.put("textBackground", Color.darkGray);
        UIManager.put("nimbusLightBackground", Color.black);

        for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
            if ("Nimbus".equals(info.getName())) {
                try {
                    UIManager.setLookAndFeel(info.getClassName());
                } catch (ClassNotFoundException | InstantiationException
                         | IllegalAccessException
                         | UnsupportedLookAndFeelException e) {
                    LOGGER.error(e.getMessage());
                }
                break;
            }
        }
    }

}