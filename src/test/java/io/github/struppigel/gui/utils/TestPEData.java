package io.github.struppigel.gui.utils;

import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.testsupport.TestFiles;

import javax.swing.*;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads samples the same way the GUI does, via {@link PELoadWorker}, without executing the worker.
 */
public final class TestPEData {

    private static final Map<String, FullPEData> cache = new ConcurrentHashMap<>();

    private TestPEData() {}

    /**
     * @param sample path of the sample relative to the corpus folder, see {@link TestFiles}
     * @return the loaded data, cached per sample; skips the calling test if the sample is not available
     */
    public static FullPEData load(String sample) throws Exception {
        File file = TestFiles.file(sample);
        FullPEData data = cache.get(sample);
        if (data == null) {
            data = load(file);
            cache.put(sample, data);
        }
        return data;
    }

    public static FullPEData load(File file) throws Exception {
        return new PELoadWorker(file, null, new JLabel()).doInBackground();
    }
}
