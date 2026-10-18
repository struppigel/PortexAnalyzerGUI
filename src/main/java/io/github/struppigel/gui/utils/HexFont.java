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

import java.awt.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The monospaced font for hex values and offsets. Java's logical Monospaced font is Courier New on Windows, which
 * is hard to read on screens, so this prefers fonts made for code.
 */
public final class HexFont {

    /** the first one that is installed is used: Windows, macOS, Linux */
    static final List<String> PREFERRED = Arrays.asList("Consolas", "Menlo", "DejaVu Sans Mono");

    private static String family;

    private HexFont() {
    }

    /**
     * @return the font family name, Java's Monospaced if none of the preferred fonts is installed
     */
    public static synchronized String family() {
        if (family == null) {
            family = pick(new HashSet<>(Arrays.asList(
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())));
        }
        return family;
    }

    static String pick(Set<String> installed) {
        for (String candidate : PREFERRED) {
            if (installed.contains(candidate)) {
                return candidate;
            }
        }
        return Font.MONOSPACED;
    }

    public static Font font(int style, int size) {
        return new Font(family(), style, size);
    }
}
