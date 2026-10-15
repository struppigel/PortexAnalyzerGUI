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
package io.github.struppigel.gui.visualizer;

import io.github.struppigel.gui.utils.PortexSwingUtils;
import io.github.struppigel.parser.PhysicalLocation;
import io.github.struppigel.tools.layout.FileRegion;
import io.github.struppigel.tools.visualizer.engine.ByteSource;
import io.github.struppigel.tools.visualizer.engine.FileVisualization;

import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the bytes of a structure or of marked bytes to a file.
 */
final class RegionSaver {

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_NAME_LENGTH = 40;

    private RegionSaver() {
    }

    /**
     * @param locations the locations of a structure
     * @param fileLength the length of the file
     * @return the byte ranges as {from, toExclusive} that lie within the file, in the order of the locations
     */
    static List<long[]> rangesOf(List<PhysicalLocation> locations, long fileLength) {
        List<long[]> ranges = new ArrayList<>();
        for (PhysicalLocation location : locations) {
            long end = Math.min(location.from() + location.size(), fileLength);
            if (location.from() >= 0 && end > location.from()) {
                ranges.add(new long[]{location.from(), end});
            }
        }
        return ranges;
    }

    /**
     * @param parent the component the dialogs are shown over
     * @return a menu item that saves the bytes of the structure, disabled if the structure has no bytes in the file
     */
    static JMenuItem menuItem(Component parent, VisualizationModel model, FileRegion region) {
        List<long[]> ranges = rangesOf(region.getLocations(), model.getVisualization().getFileLength());
        String name = region.getName();
        JMenuItem item = new JMenuItem("Save " + (name.length() > 50 ? name.substring(0, 47) + "..." : name)
                + " as file...");
        item.setEnabled(!ranges.isEmpty());
        item.addActionListener(a -> saveWithDialog(parent, model, name, ranges));
        return item;
    }

    /**
     * Asks for a file and writes the bytes of the ranges to it in the background.
     *
     * @param name the name of the structure, used for the suggested file name
     * @param ranges the ranges as {from, toExclusive}
     */
    static void saveWithDialog(Component parent, VisualizationModel model, String name, List<long[]> ranges) {
        FileVisualization vis = model.getVisualization();
        if (vis == null || ranges.isEmpty() || model.getPeData() == null) {
            return;
        }
        File input = model.getPeData().getPeData().getFile();
        String path = PortexSwingUtils.getSaveFileNameFromUser(parent,
                suggestedFileName(input, name, ranges.get(0)[0]));
        if (path == null) {
            return;
        }
        File out = new File(path);
        if (!PortexSwingUtils.checkIfFileExistsAndAskIfOverwrite(parent, out)) {
            return;
        }
        ByteSource source = vis.getSource();
        new SwingWorker<Long, Void>() {
            @Override
            protected Long doInBackground() throws Exception {
                return write(source, ranges, out);
            }

            @Override
            protected void done() {
                try {
                    JOptionPane.showMessageDialog(parent, get() + " bytes saved to " + out,
                            "Success", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    JOptionPane.showMessageDialog(parent, "Unable to save the bytes: " + cause.getMessage(),
                            "Something went wrong", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Writes the ranges one after the other.
     *
     * @param ranges the ranges as {from, toExclusive}
     * @return the number of bytes written
     * @throws IOException if the source can not be read or the file can not be written
     */
    static long write(ByteSource source, List<long[]> ranges, File out) throws IOException {
        byte[] buffer = new byte[CHUNK_SIZE];
        long written = 0;
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(out))) {
            for (long[] range : ranges) {
                long offset = range[0];
                while (offset < range[1]) {
                    int read = source.read(offset, buffer, 0, (int) Math.min(CHUNK_SIZE, range[1] - offset));
                    if (read <= 0) {
                        break;
                    }
                    stream.write(buffer, 0, read);
                    offset += read;
                    written += read;
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return written;
    }

    /**
     * @param file the analysed file
     * @param name the name of the structure
     * @param start the offset of the first byte
     * @return a file name next to the analysed file, e.g. sample.exe.Certificate_Padding_0x2f400.bin
     */
    static String suggestedFileName(File file, String name, long start) {
        String cleaned = name.replaceAll("[^A-Za-z0-9._-]+", "_");
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH);
        }
        return file.getAbsolutePath() + "." + cleaned + "_0x" + Long.toHexString(start) + ".bin";
    }
}
