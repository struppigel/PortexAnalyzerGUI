/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui.pedetails;

import com.github.struppigel.parser.IOUtil;
import com.github.struppigel.gui.FullPEData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ExecutionException;

public class FileContentPreviewPanel extends JPanel {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final int PREVIEW_SIZE = 0x2000;
    private JTextArea contentDisplay = new JTextArea("Offset: 0");

    private FullPEData pedata;
    private boolean isHexEnabled = true;

    public FileContentPreviewPanel() {
        contentDisplay.setLineWrap(true);
        this.setLayout(new BorderLayout());
        this.add(contentDisplay, BorderLayout.CENTER);
        Font font = new Font("Courier New", Font.PLAIN, 12);
        contentDisplay.setFont(font);
    }

    public void setPeData(FullPEData data) {
        this.pedata = data;
        showContentAtOffset(0);
    }

    public void showContentAtOffset(long offset) {
        if (pedata == null) {return;}

        (new SwingWorker<String, Void>() {

            @Override
            protected String doInBackground() {
                byte[] content = prepareContentString(readContentAtOffset(offset));
                String contentStr = new String(content);
                return contentStr;
            }

            protected void done() {
                try {
                    String contentStr = get();
                    String offsetStr = isHexEnabled ? "Offset: 0x" + Long.toHexString(offset) : "Offset: " + offset;
                    contentDisplay.setText(offsetStr + "\n" + contentStr);
                    contentDisplay.repaint();
                } catch (InterruptedException | ExecutionException e) {
                    LOGGER.error(e);
                }
            }
        }).execute();
    }

    private byte[] prepareContentString(byte[] arr) {
        for(int i = 0; i < arr.length; i++) {
            if(arr[i] < 32 || arr[i] == 127) {
                arr[i] = 0x2e; // '.'
            }
        }
        return arr;
    }

    private byte[] readContentAtOffset(long offset) {
        try (RandomAccessFile raf = new RandomAccessFile(pedata.getFile(), "r")) {
            return IOUtil.loadBytesSafely(offset, PREVIEW_SIZE, raf);
        } catch (IOException e) {
            LOGGER.error(e);
        }
        return new byte[0];
    }

    public void setHexEnabled(boolean hexEnabled) {
        this.isHexEnabled = hexEnabled;
    }
}
