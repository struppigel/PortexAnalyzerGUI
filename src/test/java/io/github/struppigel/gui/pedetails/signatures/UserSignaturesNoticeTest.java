package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.gui.usersignatures.UserSignatureMatches;
import io.github.struppigel.gui.usersignatures.UserSignatureSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static io.github.struppigel.testsupport.Swing.onEdt;
import static io.github.struppigel.testsupport.Swing.onEdtGet;
import static org.junit.jupiter.api.Assertions.*;

class UserSignaturesNoticeTest {

    @TempDir
    Path dir;

    private UserSignaturesNotice show(UserSignatureSet signatures, Optional<String> error) throws Exception {
        return onEdtGet(() -> {
            UserSignaturesNotice notice = new UserSignaturesNotice();
            notice.showSignatures(signatures, error);
            return notice;
        });
    }

    @Test
    void hiddenWithoutUserSignatures() throws Exception {
        UserSignaturesNotice notice = onEdtGet(UserSignaturesNotice::new);
        assertFalse(notice.isVisible());
        onEdt(() -> notice.showSignatures(UserSignatureMatches.NONE));
        assertFalse(notice.isVisible());
    }

    @Test
    void emptyFolderTellsWhereToAddSignatures() throws Exception {
        UserSignaturesNotice notice = show(UserSignatureSet.load(dir.toFile()), Optional.empty());

        assertTrue(notice.isVisible());
        assertEquals("Your signatures: none, add .sig files to " + dir.toFile().getAbsolutePath(),
                notice.getStatusText());
        assertEquals("", notice.getWarningTitle());
        assertEquals("", notice.getWarningsText());
    }

    @Test
    void skippedSignaturesAreListedWithFileAndLine() throws Exception {
        Files.write(dir.resolve("broken.sig"), "[Broken]\npattern = 0G\nat = file\n\n[Fine]\npattern = 'MZ'\nat = file\n"
                .getBytes(StandardCharsets.UTF_8));
        UserSignaturesNotice notice = show(UserSignatureSet.load(dir.toFile()), Optional.empty());

        assertEquals("Your signatures: 1 from 1 file in " + dir.toFile().getAbsolutePath(), notice.getStatusText());
        assertEquals("Some of your signatures were skipped:", notice.getWarningTitle());
        assertTrue(notice.getWarningsText().startsWith("broken.sig:2:"), notice.getWarningsText());
    }

    @Test
    void scanErrorIsShown() throws Exception {
        UserSignaturesNotice notice = show(UserSignatureSet.load(dir.toFile()), Optional.of("Could not scan"));

        assertEquals("Your signatures could not be scanned:", notice.getWarningTitle());
        assertEquals("Could not scan", notice.getWarningsText());
    }
}
