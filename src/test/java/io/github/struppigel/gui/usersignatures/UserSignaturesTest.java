package io.github.struppigel.gui.usersignatures;

import io.github.struppigel.parser.AnalysisInterruptedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class UserSignaturesTest {

    @TempDir
    Path dir;

    private static String entry(String name) {
        return "[" + name + "]\npattern = 01 02\nat = file\n";
    }

    private Path write(String fileName, String text) throws IOException {
        Path file = dir.resolve(fileName);
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private static List<String> names(UserSignatureSet signatures) {
        return signatures.getDatabase().getSignatures().stream().map(s -> s.getName()).collect(Collectors.toList());
    }

    @Test
    void missingFolderHasNoSignaturesAndNoWarnings() {
        File missing = dir.resolve("missing").toFile();
        UserSignatureSet signatures = new UserSignatures(missing).current();

        assertEquals(missing, signatures.getFolder());
        assertFalse(signatures.folderExists());
        assertTrue(signatures.isEmpty());
        assertTrue(signatures.getLoadWarnings().isEmpty());
    }

    @Test
    void loadsTheSigFilesOfTheFolder() throws IOException {
        write("a.sig", entry("A"));
        write("B.SIG", entry("B"));
        write("notes.txt", entry("Notes"));
        UserSignatureSet signatures = new UserSignatures(dir.toFile()).current();

        assertTrue(signatures.folderExists());
        assertEquals(List.of("A", "B"), names(signatures));
        assertEquals(2, signatures.getFileCount());
        assertEquals(2, signatures.getSignatureCount());
        assertFalse(signatures.hasSkippedSignatures());
    }

    @Test
    void malformedEntriesAreWarnings() throws IOException {
        write("broken.sig", "[Broken]\npattern = 0G\nat = file\n\n" + entry("Fine"));
        UserSignatureSet signatures = new UserSignatures(dir.toFile()).current();

        assertEquals(List.of("Fine"), names(signatures));
        assertTrue(signatures.hasSkippedSignatures());
        assertEquals(1, signatures.getLoadWarnings().size(), signatures.getLoadWarnings().toString());
        assertTrue(signatures.getLoadWarnings().get(0).startsWith("broken.sig:2:"), signatures.getLoadWarnings().get(0));
    }

    @Test
    void signaturesAreLoadedOnlyOnceIfNothingChanged() throws IOException {
        write("a.sig", entry("A"));
        UserSignatures userSignatures = new UserSignatures(dir.toFile());

        assertSame(userSignatures.current(), userSignatures.current());
    }

    @Test
    void changedFilesAreLoadedAgain() throws IOException {
        Path a = write("a.sig", entry("A"));
        UserSignatures userSignatures = new UserSignatures(dir.toFile());
        UserSignatureSet first = userSignatures.current();

        write("a.sig", entry("A") + "\n" + entry("A2"));
        // file systems with coarse time stamps would not notice a change in the same second
        assertTrue(a.toFile().setLastModified(a.toFile().lastModified() + 2000));
        UserSignatureSet edited = userSignatures.current();
        assertNotSame(first, edited);
        assertEquals(List.of("A", "A2"), names(edited));

        write("b.sig", entry("B"));
        UserSignatureSet added = userSignatures.current();
        assertEquals(List.of("A", "A2", "B"), names(added));

        Files.delete(a);
        UserSignatureSet deleted = userSignatures.current();
        assertEquals(List.of("B"), names(deleted));
    }

    @Test
    void createdFolderIsLoaded() throws IOException {
        Path folder = dir.resolve("signatures");
        UserSignatures userSignatures = new UserSignatures(folder.toFile());
        assertFalse(userSignatures.current().folderExists());

        Files.createDirectories(folder);
        Files.write(folder.resolve("a.sig"), entry("A").getBytes(StandardCharsets.UTF_8));
        assertEquals(List.of("A"), names(userSignatures.current()));
    }

    @Test
    void invalidatedSignaturesAreLoadedAgain() throws IOException {
        write("a.sig", entry("A"));
        UserSignatures userSignatures = new UserSignatures(dir.toFile());
        UserSignatureSet first = userSignatures.current();

        userSignatures.invalidate();
        assertNotSame(first, userSignatures.current());
        assertNotSame(first, userSignatures.reload());
    }

    @Test
    void otherFolder() throws IOException {
        write("a.sig", entry("A"));
        Path other = Files.createDirectory(dir.resolve("other"));
        Files.write(other.resolve("b.sig"), entry("B").getBytes(StandardCharsets.UTF_8));
        UserSignatures userSignatures = new UserSignatures(dir.toFile());
        assertEquals(List.of("A"), names(userSignatures.current()));

        userSignatures.setFolder(other.toFile());
        assertEquals(other.toFile(), userSignatures.getFolder());
        assertEquals(List.of("B"), names(userSignatures.current()));
    }

    @Test
    void noneLoadsNothing() {
        UserSignatures none = UserSignatures.none();
        assertSame(UserSignatureSet.NONE, none.current());
        assertNull(none.getFolder());
    }

    @Test
    void interruptedLoadIsNotKept() throws IOException {
        write("a.sig", entry("A"));
        UserSignatures userSignatures = new UserSignatures(dir.toFile());
        Thread.currentThread().interrupt();
        try {
            assertThrows(AnalysisInterruptedException.class, userSignatures::current);
        } finally {
            Thread.interrupted();
        }
        assertEquals(List.of("A"), names(userSignatures.current()));
    }

    @Test
    void labelNamesTheDatabase() throws IOException {
        write("mine.sig", "[My magic]\npattern = 'MAGIC'\nin = file\n");
        UserSignatureSet signatures = new UserSignatures(dir.toFile()).current();
        Path sample = write("sample.bin", "xxMAGICxx");

        UserSignatureMatches matches = UserSignatureMatches.scanFile(signatures, sample.toFile());
        assertEquals(1, matches.getMatches().size());
        assertEquals("My magic from mine.sig", UserSignatureSet.label(matches.getMatches().get(0)));
        assertTrue(matches.getError().isEmpty());
    }
}
