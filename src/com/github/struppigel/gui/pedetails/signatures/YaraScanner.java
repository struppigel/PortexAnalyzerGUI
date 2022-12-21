/**
 * *****************************************************************************
 * Copyright 2022 Karsten Hahn
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * <a href="http://www.apache.org/licenses/LICENSE-2.0">http://www.apache.org/licenses/LICENSE-2.0</a>
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package com.github.struppigel.gui.pedetails.signatures;

import com.github.struppigel.gui.FullPEData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

class YaraScanner extends SwingWorker<List<YaraRuleMatch>, Void> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final SignaturesPanel signaturesPanel;
    private final FullPEData pedata;
    private final String yaraPath;
    private final String rulePath;

    public YaraScanner(SignaturesPanel signaturesPanel, FullPEData data, String yaraPath, String rulePath) {
        this.signaturesPanel = signaturesPanel;
        this.pedata = data;
        this.yaraPath = yaraPath;
        this.rulePath = rulePath;
    }

    @Override
    protected List<YaraRuleMatch> doInBackground() throws IOException {
        ProcessBuilder pbuilder = new ProcessBuilder(yaraPath, "-s", rulePath, pedata.getFile().getAbsolutePath());
        Process process = null;
        List<YaraRuleMatch> matches = new ArrayList<>();
        try {
            process = pbuilder.start();
            stdInHandling(process, matches);
            stdErrHandling(process);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return matches;
    }

    private void stdErrHandling(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                LOGGER.error("Error while parsing yara rules: " + line);
                JOptionPane.showMessageDialog(signaturesPanel,
                        line,
                        "Rule parsing error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void stdInHandling(Process process, List<YaraRuleMatch> matches) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            List<PatternMatch> patterns = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                patterns = scanLine(matches, patterns, line);
            }
        }
    }

    private List<PatternMatch> scanLine(List<YaraRuleMatch> matches, List<PatternMatch> patterns, String line) {
        if (isRuleName(line)) {
            String rulename = parseRulename(line);
            patterns = new ArrayList<>(); // fresh patterns
            matches.add(new YaraRuleMatch(rulename, patterns, new ArrayList<>()));
        } else if (isPattern(line)) {
            PatternMatch pattern = parsePattern(line);
            patterns.add(pattern);
        }
        return patterns;
    }

    private PatternMatch parsePattern(String line) {
        assert isPattern(line);
        String[] split = line.split(":");
        String longStr = split[0].substring(2);// remove '0x'
        long offset = Long.parseLong(longStr, 16); // convert from hex
        String name = split[1];
        String content = String.join(":", Arrays.copyOfRange(split, 2, split.length));
        return new PatternMatch(offset, name, content);
    }

    private boolean isPattern(String line) {
        return line.startsWith("0x") && line.contains(":$") &&
                line.split(":").length >= 3 && isLongHex(line.split(":\\$")[0]);
    }

    private boolean isLongHex(String s) {
        if (s.startsWith("0x")) {
            return isLongHex(s.substring(2));
        }
        try {
            Long.parseLong(s, 16);
            return true;
        } catch (NumberFormatException e) {
            LOGGER.warn("This is not a long " + s);
            return false;
        }
    }

    private String parseRulename(String line) {
        assert isRuleName(line);
        return line.split(" ")[0];
    }

    private boolean isRuleName(String line) {
        return !isPattern(line) && line.split(" ").length >= 2;
    }

    @Override
    protected void done() {
        try {
            signaturesPanel.buildYaraTables(get());
        } catch (ExecutionException e) {
            // most commonly when user did not choose valid yara path or signature path
            // so we request them again
            signaturesPanel.requestPaths();
            JOptionPane.showMessageDialog(signaturesPanel,
                    e.getMessage(),
                    "IO Error",
                    JOptionPane.ERROR_MESSAGE);
            LOGGER.error(e);
        } catch (InterruptedException e) {
            e.printStackTrace();
            LOGGER.error(e);
        }
    }
}
