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

import io.github.struppigel.settings.LookAndFeelSetting;
import io.github.struppigel.settings.PortexSettings;
import io.github.struppigel.settings.PortexSettingsKey;

import javax.swing.*;

/**
 * One-color icons that exist in a light version for the dark PortEx theme and in a dark version for the bright
 * system theme, so that they are visible in both.
 */
public enum ThemeIcon {
    HEX("/icons8-hexadecimal-24.png", "/icons8-hexadecimal-24black.png"),
    IMAGE("/icons8-image-24.png", "/icons8-image-24black.png"),
    OPEN("/laptop-bug-icon-white.png", "/laptop-bug-icon.png"),
    EXIT("/moon-icon-white.png", "/moon-icon.png");

    private final String forDarkTheme;
    private final String forBrightTheme;

    ThemeIcon(String forDarkTheme, String forBrightTheme) {
        this.forDarkTheme = forDarkTheme;
        this.forBrightTheme = forBrightTheme;
    }

    /**
     * @param darkTheme true for the dark PortEx theme
     * @return the resource path of the icon for the theme
     */
    public String path(boolean darkTheme) {
        return darkTheme ? forDarkTheme : forBrightTheme;
    }

    public ImageIcon load(boolean darkTheme) {
        return new ImageIcon(ThemeIcon.class.getResource(path(darkTheme)));
    }

    /**
     * @return true if the GUI uses the dark PortEx theme. A changed setting applies after a restart, so this must be
     * asked when the GUI is created.
     */
    public static boolean isDarkTheme(PortexSettings settings) {
        return settings.valueEquals(PortexSettingsKey.LOOK_AND_FEEL, LookAndFeelSetting.PORTEX.toString());
    }
}
