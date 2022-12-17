package com.github.struppigel.gui;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class PortexSwingUtils {

    public static String getOpenFileNameFromUser(Component parent) {
        File userdir = new File(System.getProperty("user.dir"));
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

    public static String getSaveFileNameFromUser(Component parent) {
        File userdir = new File(System.getProperty("user.dir"));
        JFileChooser fc = new JFileChooser(userdir);
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
