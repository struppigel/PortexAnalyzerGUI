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
package com.github.struppigel.gui.utils;

import com.github.struppigel.gui.pedetails.PEDetailsPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class PortexSwingUtils {
    private static final File userdir = new File(System.getProperty("user.dir"));

    public static String getOpenFileNameFromUser(Component parent) {
        JFileChooser fc = new JFileChooser(userdir);
        int state = fc.showOpenDialog(parent);

        if (state == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (file != null) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    public static String getSaveFileNameFromUser(Component parent, String defaultFileName) {
        JFileChooser fc = new JFileChooser(userdir);
        fc.setSelectedFile(new File(defaultFileName));
        int state = fc.showSaveDialog(parent);
        if (state == JFileChooser.APPROVE_OPTION) {
            File dir = fc.getCurrentDirectory();
            File file = fc.getSelectedFile();
            if (file != null && file.isDirectory()) {
                return file.getAbsolutePath();
            }
            return dir.getAbsolutePath();

        }
        return null;
    }

    public static String getSaveFolderNameFromUser(PEDetailsPanel parent) {
        JFileChooser fc = new JFileChooser(userdir);
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setAcceptAllFileFilterUsed(false);
        int state = fc.showSaveDialog(parent);
        if (state == JFileChooser.APPROVE_OPTION) {
            File file = fc.getSelectedFile();
            if (file != null) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }
}
