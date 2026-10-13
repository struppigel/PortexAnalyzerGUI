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

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import io.github.struppigel.settings.LookAndFeelSetting;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.settings.PortexSettingsKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Sets the look and feel and starts the GUI
 */
public class Starter {
    private static final Logger LOGGER = LogManager.getLogger();
    // the dark red of the PortEx logo
    private static final String ACCENT_COLOR = "#AA0000";

    public static void main(String[] args) {

        LOGGER.debug("starting program");
        PortexSettings s = new PortexSettings();
        if(s.valueEquals(PortexSettingsKey.LOOK_AND_FEEL, LookAndFeelSetting.PORTEX.toString())) {
            setDarkLookAndFeel();
        } else {
            setBrightLookAndFeel();
        }
        initMainFrame(s);
    }

    private static void initMainFrame(PortexSettings s) {
        SwingUtilities.invokeLater(() -> new MainFrame(s));
    }

    public static void setDarkLookAndFeel() {
        Map<String, String> colors = new HashMap<>();
        // neutral dark, red only marks selection, focus and pressed controls
        colors.put("@background", "#1A1A1A");
        colors.put("@foreground", "#F2F2F2");
        colors.put("@componentBackground", "#000000");
        colors.put("@menuBackground", "#121212");
        colors.put("@buttonBackground", "#2E2E2E");
        colors.put("Table.alternateRowColor", "#1E1E1E");
        colors.put("TableHeader.background", "#2A2A2A");
        colors.put("TableHeader.separatorColor", "#444444");
        colors.put("Component.borderColor", "#3C3C3C");
        colors.put("Separator.foreground", "#3C3C3C");
        colors.put("ScrollBar.thumb", "#3C3C3C");
        colors.put("ScrollBar.hoverThumbColor", "#555555");
        colors.put("ScrollBar.pressedThumbColor", ACCENT_COLOR);
        colors.put("ToolTip.background", "#282828");
        colors.put("ToolTip.foreground", "#FFFFFF");
        setup(new FlatDarkLaf(), colors);
    }

    public static void setBrightLookAndFeel() {
        Map<String, String> colors = new HashMap<>();
        colors.put("Table.alternateRowColor", "#F0F0F0");
        setup(new FlatLightLaf(), colors);
    }

    private static void setup(LookAndFeel laf, Map<String, String> colors) {
        Map<String, String> defaults = new HashMap<>(colors);
        defaults.put("@accentColor", ACCENT_COLOR);
        FlatLaf.setGlobalExtraDefaults(defaults);
        try {
            UIManager.setLookAndFeel(laf);
        } catch (UnsupportedLookAndFeelException e) {
            LOGGER.error(e.getMessage());
        }
    }

}

