package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.tools.sigscanner.ClauseMatch;
import io.github.struppigel.tools.sigscanner.LocationKind;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import io.github.struppigel.gui.FullPEData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Collects the signature matches of PortEx for the signatures panel.
 * The matches are computed and cached by PortEx while the file is loaded, so this does not scan again.
 */
public class PEidScanner extends SwingWorker<List<PEidRuleMatch>, Void> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String SOURCE = "PortEx";
    private final SignaturesPanel signaturesPanel;
    private final FullPEData pedata;

    public PEidScanner(SignaturesPanel signaturesPanel, FullPEData data) {
        this.signaturesPanel = signaturesPanel;
        this.pedata = data;
    }

    @Override
    protected List<PEidRuleMatch> doInBackground() {
        // all default locations, never LocationKind.FILE, which would match every PE file
        return toRuleMatches(pedata.getPeData().getSignatureMatches());
    }

    static List<PEidRuleMatch> toRuleMatches(List<SignatureMatch> matches) {
        // one rule match per location kind and signature name, in the order of the matches
        Map<String, List<PatternMatch>> patternsByRule = new LinkedHashMap<>();
        Map<String, String[]> ruleInfo = new LinkedHashMap<>();
        for (SignatureMatch m : matches) {
            String scanMode = scanModeOf(m.getLocationKind());
            String key = scanMode + "\u0000" + m.getName();
            ruleInfo.putIfAbsent(key, new String[]{m.getName(), scanMode});
            patternsByRule.computeIfAbsent(key, k -> new ArrayList<>()).addAll(toPatternMatches(m));
        }
        List<PEidRuleMatch> result = new ArrayList<>();
        for (Map.Entry<String, List<PatternMatch>> e : patternsByRule.entrySet()) {
            String[] info = ruleInfo.get(e.getKey());
            result.add(new PEidRuleMatch(info[0], info[1], e.getValue(), SOURCE));
        }
        return result;
    }

    private static List<PatternMatch> toPatternMatches(SignatureMatch m) {
        List<PatternMatch> result = new ArrayList<>();
        // the pattern name is empty because it looks weird to see the same value for rule name and pattern name
        for (ClauseMatch cm : m.getClauseMatches()) {
            String location = cm.getRegion().getLabel() + "+0x" + Long.toHexString(cm.getRelativeOffset());
            result.add(new PatternMatch(cm.getOffset(), "", cm.getClause().getPattern().getSource(), location));
        }
        if (result.isEmpty()) { // signatures without pattern only match by their conditions
            result.add(new PatternMatch(null, "", "", m.getLocationLabel()));
        }
        return result;
    }

    static String scanModeOf(LocationKind kind) {
        switch (kind) {
            case ENTRY_POINT: return "Entry Point";
            case SECTION: return "Section";
            case OVERLAY: return "Overlay";
            case RESOURCE: return "Resource";
            case MSDOS_HEADER: return "MS-DOS Header";
            case MSDOS_STUB: return "MS-DOS Stub";
            case RICH_HEADER: return "Rich Header";
            case DOTNET_STREAM: return ".NET Stream";
            default:
                String name = kind.name().replace('_', ' ').toLowerCase();
                return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }
    }

    @Override
    protected void done() {
        if(isCancelled()){return;}
        try {
            signaturesPanel.buildPEiDTables(get());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            LOGGER.error(e);
        }

    }
}
