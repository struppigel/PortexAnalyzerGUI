package com.github.struppigel.gui.pedetails.signatures;

import com.github.katjahahn.parser.sections.rsrc.Resource;
import com.github.katjahahn.tools.Overlay;
import com.github.katjahahn.tools.sigscanner.FileTypeScanner;
import com.github.katjahahn.tools.sigscanner.SignatureScanner;
import com.github.struppigel.gui.FullPEData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
        List<PEidRuleMatch> result = entryPointScan();  // epOnly
        result.addAll(overlayScan());                   // Overlay
        result.addAll(resourceScan());                  // Resource
        return result;
    }

    private List<PEidRuleMatch> entryPointScan() {
        List<PEidRuleMatch> result;
        List<SignatureScanner.SignatureMatch> matches = SignatureScanner.newInstance().scanAll(pedata.getFile(), true);
        result = toPeidRuleMatches(toPatternMatches(matches), "Entry Point");
        return result;
    }

    private List<PEidRuleMatch> overlayScan() {
        Overlay overlay = pedata.getOverlay();
        List<PEidRuleMatch> result = new ArrayList<>();
        try {
            long offset = overlay.getOffset();
            List<SignatureScanner.SignatureMatch> overlayMatches = new SignatureScanner(SignatureScanner.loadOverlaySigs()).scanAt(pedata.getFile(), offset);
            List<PEidRuleMatch> oMatch = toPeidRuleMatches(toPatternMatches(overlayMatches), "Overlay");
            result.addAll(oMatch);
        } catch (IOException e) {
            LOGGER.error("something went wrong while scanning the overlay " + e);
        }
        return result;
    }

    private List<PEidRuleMatch> resourceScan() {
        List<PatternMatch> resMatches = new ArrayList<>();
        for (Resource r : pedata.getResources()) {
            long resOffset = r.rawBytesLocation().from();
            List<SignatureScanner.SignatureMatch> filetypes = FileTypeScanner.apply(pedata.getFile()).scanAt(resOffset);
            resMatches.addAll(toPatternMatches(filetypes));
        }
        return toPeidRuleMatches(resMatches, "Resource");
    }


    private List<PatternMatch> toPatternMatches(List<SignatureScanner.SignatureMatch> matches){
        return matches.stream().map(m -> toPatternMatch(m)).collect(Collectors.toList());
    }

    private PatternMatch toPatternMatch(SignatureScanner.SignatureMatch m) {
        String rulename = m.signature().name();
        if (rulename.startsWith("[")) {
            rulename = rulename.substring(1, rulename.length() - 1);
        }
        String pattern = m.signature().signatureString();
        long offset = m.address();
        return new PatternMatch(offset, rulename, pattern);
    }

    private List<PEidRuleMatch> toPeidRuleMatches(List<PatternMatch> patterns, String scanMode) {
        List<PEidRuleMatch> result = new ArrayList<>();
        // create unique list of all rule names
        Set<String> uniqueRulenames = new HashSet<>();
        for(PatternMatch pattern : patterns) {
            uniqueRulenames.add(pattern.patternName);
        }
        // for every unique rule name get all patterns
        for(String rule : uniqueRulenames) {
            List<PatternMatch> rulePatterns = patterns.stream().filter(p -> p.patternName.equals(rule)).collect(Collectors.toList());
            // make pattern name empty string because it looks weird to see the same value for rule name and pattern name
            rulePatterns =  rulePatterns.stream().map(p -> new PatternMatch(p.offset, "", p.patternContent)).collect(Collectors.toList());
            // create a PEiD rule match out of these patterns
            result.add(new PEidRuleMatch(rule, scanMode, rulePatterns));
        }
        return result;
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
