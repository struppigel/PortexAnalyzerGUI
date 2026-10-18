package io.github.struppigel.gui.visualizer;

import io.github.struppigel.testsupport.TestFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.github.struppigel.testsupport.TestFiles.NATIVE_X86;
import static org.junit.jupiter.api.Assertions.*;

class ClassicImageExporterTest {

    @Test
    void nonPEFileHasNoStructureColumn(@TempDir Path dir) throws Exception {
        File text = dir.resolve("text.txt").toFile();
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            content.append("no PE file, just some text ").append(i).append('\n');
        }
        Files.write(text.toPath(), content.toString().getBytes(StandardCharsets.US_ASCII));

        BufferedImage notPE = ClassicImageExporter.render(text, ClassicImageExporter.DEFAULT_HEIGHT);
        BufferedImage pe = ClassicImageExporter.render(TestFiles.file(NATIVE_X86), ClassicImageExporter.DEFAULT_HEIGHT);

        assertTrue(notPE.getHeight() > 0);
        // byte plot, entropy and legend, but no structure
        assertTrue(notPE.getWidth() < pe.getWidth(), notPE.getWidth() + " vs. " + pe.getWidth());
    }
}
