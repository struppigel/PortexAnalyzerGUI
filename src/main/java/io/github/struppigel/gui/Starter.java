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

import io.github.struppigel.settings.LookAndFeelSetting;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.settings.PortexSettingsKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sets the look and feel and starts the GUI
 */
public class Starter {
    private static final Logger LOGGER = LogManager.getLogger();

    public static void main(String[] args) {

        LOGGER.debug("starting program");
        PortexSettings s = new PortexSettings();
        if(s.valueEquals(PortexSettingsKey.LOOK_AND_FEEL, LookAndFeelSetting.PORTEX.toString())) {
            setPortexLookAndFeel();
        } else {
            setSystemLookAndFeel();
        }
        initMainFrame(s);
    }

    private static void initMainFrame(PortexSettings s) {
        SwingUtilities.invokeLater(() -> new MainFrame(s));
    }

    private static void setSystemLookAndFeel() {
        try {
            // Set System L&F
            UIManager.setLookAndFeel(
                    UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Table.alternateRowColor", new Color(240,240,240));
        }
        catch (ClassNotFoundException | InstantiationException
               | IllegalAccessException
               | UnsupportedLookAndFeelException e) {
            LOGGER.error(e.getMessage());
        }
    }

    /**
     * @return the colors of the PortEx theme, a dark Nimbus, as keys and values for the UIManager
     */
    public static Map<String, Color> portexThemeColors() {
        Map<String, Color> colors = new LinkedHashMap<>();
        colors.put("nimbusBase", new Color(15, 0, 0));
        colors.put("nimbusBlueGrey", new Color(170, 0, 0));
        colors.put("control", Color.black);
        colors.put("text", Color.white);
        colors.put("nimbusSelectionBackground", Color.gray);
        colors.put("nimbusSelectedText", Color.white);
        colors.put("textHighlight", Color.lightGray);
        colors.put("nimbusFocus", new Color(0xBA3A1E));
        colors.put("nimbusSelection", new Color(170, 0, 0));
        colors.put("textBackground", Color.darkGray);
        colors.put("nimbusLightBackground", Color.black);
        // background of tool tips, otherwise Nimbus paints the white text on light yellow
        colors.put("info", new Color(40, 40, 40));
        colors.put("Table.alternateRowColor", new Color(20,20,20));
        return colors;
    }

    public static void setPortexLookAndFeel() {
        portexThemeColors().forEach(UIManager::put);

       // UIManager.put("ToggleButton.disabled", Color.yellow);
        //UIManager.put("ToggleButton.foreground", Color.yellow);
        //UIManager.put("ToolBar.opaque", true);
        //UIManager.put("ToolBar.background", new Color(100, 0, 0));
        //UIManager.put("ToolBar.disabled", Color.green);
        //UIManager.put("ToolBar.foreground", Color.red);
/*
        UIManager.put("ToolBar.background", Color.blue);
        UIManager.put("ToolBar.foreground", Color.blue);
        UIManager.put("ToolBar.disabled", Color.blue);
        UIManager.put("ToolBar.opaque", false);
*/

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