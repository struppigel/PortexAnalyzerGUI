package io.github.struppigel.gui.pedetails.signatures;

import io.github.struppigel.tools.sigscanner.ClauseMatch;
import io.github.struppigel.tools.sigscanner.LocationKind;
import io.github.struppigel.tools.sigscanner.SignatureMatch;
import io.github.struppigel.gui.FileData;
import io.github.struppigel.gui.usersignatures.UserSignatureSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Collects the signature matches of PortEx and of the user's signatures for the signatures panel.
 * The matches are computed while the file is loaded, so this does not scan again.
 */
public class PEidScanner extends SwingWorker<List<PEidRuleMatch>, Void> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final String SOURCE = "PortEx";
    private final SignaturesPanel signaturesPanel;
    private final FileData data;

    public PEidScanner(SignaturesPanel signaturesPanel, FileData data) {
        this.signaturesPanel = signaturesPanel;
        this.data = data;
    }

    @Override
    protected List<PEidRuleMatch> doInBackground() {
        // the user's signatures first, because the user is usually interested in them;
        // PE files: all locations for the user's signatures, the bundled ones skip LocationKind.FILE, which would
        // match every PE file; other files: only the locations file and eof
        List<PEidRuleMatch> result = new ArrayList<>(
                toRuleMatches(data.getUserSignatureMatches(), UserSignatureSet::sourceOf));
        result.addAll(toRuleMatches(data.getSignatureMatches()));
        return result;
    }

    /**
     * @param matches matches of the bundled signatures
     */
    static List<PEidRuleMatch> toRuleMatches(List<SignatureMatch> matches) {
        return toRuleMatches(matches, m -> SOURCE);
    }

    /**
     * @param sourceOf the source column of a match, e.g. the database of the user's signature
     */
    static List<PEidRuleMatch> toRuleMatches(List<SignatureMatch> matches, Function<SignatureMatch, String> sourceOf) {
        // one rule match per source, location kind and signature name, in the order of the matches
        Map<String, List<PatternMatch>> patternsByRule = new LinkedHashMap<>();
        Map<String, String[]> ruleInfo = new LinkedHashMap<>();
        for (SignatureMatch m : matches) {
            String source = sourceOf.apply(m);
            String scanMode = scanModeOf(m.getLocationKind());
            String key = source + "\u0000" + scanMode + "\u0000" + m.getName();
            ruleInfo.putIfAbsent(key, new String[]{m.getName(), scanMode, source});
            patternsByRule.computeIfAbsent(key, k -> new ArrayList<>()).addAll(toPatternMatches(m));
        }
        List<PEidRuleMatch> result = new ArrayList<>();
        for (Map.Entry<String, List<PatternMatch>> e : patternsByRule.entrySet()) {
            String[] info = ruleInfo.get(e.getKey());
            result.add(new PEidRuleMatch(info[0], info[1], e.getValue(), info[2]));
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
            case FILE: return "File";
            case EOF: return "End of file";
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
