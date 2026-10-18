package io.github.struppigel.gui.utils;

import io.github.struppigel.gui.FileData;
import io.github.struppigel.gui.FullPEData;
import io.github.struppigel.gui.usersignatures.UserSignatures;
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
        return loadAny(file).asPE().orElseThrow(() -> new AssertionError(file + " was not loaded as PE file"));
    }

    /**
     * @return the loaded data of any file, also of files that are no PE files; not cached
     */
    public static FileData loadAny(File file) throws Exception {
        return new PELoadWorker(file, null, new JLabel()).doInBackground();
    }

    /**
     * @param signatureFolder the folder of the user's signatures that the file is scanned with
     * @return the loaded data of any file, also of files that are no PE files; not cached
     */
    public static FileData loadAny(File file, File signatureFolder) throws Exception {
        return new PELoadWorker(file, null, new JLabel(), new UserSignatures(signatureFolder)).doInBackground();
    }
}
