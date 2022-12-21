package com.github.struppigel.gui.pedetails.signatures;

import com.github.katjahahn.tools.sigscanner.SignatureScanner;
import com.github.struppigel.gui.FullPEData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class PEidScanner extends SwingWorker<List<PEidRuleMatch>, Void> {
    private static final Logger LOGGER = LogManager.getLogger();
    private final SignaturesPanel signaturesPanel;
    private final FullPEData pedata;

    public PEidScanner(SignaturesPanel signaturesPanel, FullPEData data) {
        this.signaturesPanel = signaturesPanel;
        this.pedata = data;
    }

    @Override
    protected List<PEidRuleMatch> doInBackground() {
        List<SignatureScanner.SignatureMatch> matches = SignatureScanner.newInstance().scanAll(pedata.getFile(), true);
        return matches.stream().map(m -> convertToPeidRuleMatch(m)).collect(Collectors.toList());
    }

    private PEidRuleMatch convertToPeidRuleMatch(SignatureScanner.SignatureMatch m) {
        String rulename = m.signature().name().substring(1,m.signature().name().length() - 1);
        String pattern = m.signature().signatureString();
        String location = "EP only";
        long offset = m.address();
        return new PEidRuleMatch(rulename, pattern, location, offset);
    }

    @Override
    protected void done() {
        try {
            List<PEidRuleMatch> matches = get();
            signaturesPanel.buildPEiDTables(get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            LOGGER.error(e);
        }

    }
}
