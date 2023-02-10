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

import com.github.struppigel.settings.LookAndFeelSetting;
import com.github.struppigel.settings.PortexSettings;
import com.github.struppigel.settings.PortexSettingsKey;
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
        }
        catch (ClassNotFoundException | InstantiationException
               | IllegalAccessException
               | UnsupportedLookAndFeelException e) {
            LOGGER.error(e.getMessage());
        }
    }

    private static void setPortexLookAndFeel() {
        UIManager.put("nimbusBase", new Color(15, 0, 0));
        UIManager.put("nimbusBlueGrey", new Color(170, 0, 0));
        UIManager.put("control", Color.black);

       // UIManager.put("ToggleButton.disabled", Color.yellow);
        //UIManager.put("ToggleButton.foreground", Color.yellow);
        //UIManager.put("ToolBar.opaque", true);
        //UIManager.put("ToolBar.background", new Color(100, 0, 0));
        //UIManager.put("ToolBar.disabled", Color.green);
        //UIManager.put("ToolBar.foreground", Color.red);

        UIManager.put("text", Color.white);

        UIManager.put("nimbusSelectionBackground", Color.gray);
        UIManager.put("nimbusSelectedText", Color.white);
        UIManager.put("textHighlight", Color.lightGray);
        UIManager.put("nimbusFocus", new Color(0xBA3A1E));
        UIManager.put("nimbusSelection", new Color(170, 0, 0));
        UIManager.put("textBackground", Color.darkGray);
        UIManager.put("nimbusLightBackground", Color.black);
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