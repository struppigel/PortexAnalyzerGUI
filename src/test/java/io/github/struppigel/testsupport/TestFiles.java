package io.github.struppigel.testsupport;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Locates PE samples in the local PortEx test corpus.
 * <p>
 * The samples are never committed to this repository because they can be malicious. They are read in place from the
 * folder given by the system property portex.testfiles, which defaults to ../PortEx/portextestfiles. Tests that need
 * a sample are skipped if it is not available.
 */
public final class TestFiles {

    public static final String DOTNET = "testfiles/HelloWorld.exe";
    public static final String DOTNET_CORE = "testfiles/NetCoreConsole.dll";
    public static final String DOTNET_PINVOKE = "testfiles/pinvoke";
    public static final String OVERLAY = "testfiles/embedded_exe_overlay";
    public static final String RESOURCES = "testfiles/embedded_exe_resources";
    public static final String SMALLEST_PE = "testfiles/smallest-pe.exe";
    public static final String DEBUG_INFO = "testfiles/TestCetCompatAndEhCont.exe";
    public static final String NATIVE_X86 = "Haus/TLSCallback_x86.exe";
    public static final String ICONS_X64 = "Haus/add-import.exe";
    public static final String NOT_PE = "Haus/notPeFile.txt";

    public static final List<String> ALL_PE = Arrays.asList(
            DOTNET, OVERLAY, RESOURCES, SMALLEST_PE, DEBUG_INFO, NATIVE_X86, ICONS_X64);

    public static final List<String> ALL_DOTNET = Arrays.asList(DOTNET, DOTNET_CORE, DOTNET_PINVOKE);

    private TestFiles() {}

    /**
     * @param relativePath path of the sample relative to the corpus folder
     * @return the sample file; skips the calling test if the sample is not available
     */
    public static File file(String relativePath) {
        String root = System.getProperty("portex.testfiles", "../PortEx/portextestfiles");
        File file = new File(root, relativePath);
        assumeTrue(file.isFile(), "PE sample not available: " + file.getAbsolutePath());
        return file;
    }
}
