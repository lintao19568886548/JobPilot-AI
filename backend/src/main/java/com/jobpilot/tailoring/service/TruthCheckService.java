package com.jobpilot.tailoring.service;

import static com.jobpilot.tailoring.dto.TailoringDtos.*;

import com.jobpilot.common.exception.BusinessException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TruthCheckService {
    private static final Pattern TOKEN = Pattern.compile("[\\p{IsHan}]{2,8}|[A-Za-z][A-Za-z0-9+#.\\-]{1,30}");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?%?");

    public void tailor(List<AiTailorChange> changes, List<EvidenceView> evidence) {
        Map<String, String> allowed = new HashMap<>();
        evidence.forEach(item -> allowed.put(item.evidenceRef(), item.text()));
        if (changes == null || changes.isEmpty()) fail("Tailor produced no reviewable changes");
        for (AiTailorChange change : changes) {
            if (change.after() == null || change.after().isBlank() || change.evidenceRefs() == null || change.evidenceRefs().isEmpty()) {
                fail("Every Tailor change must include After and Evidence Refs");
            }
            StringBuilder support = new StringBuilder(change.before() == null ? "" : change.before());
            for (String ref : change.evidenceRefs()) {
                String text = allowed.get(ref);
                if (text == null) fail("Tailor referenced unknown or cross-user evidence");
                support.append(' ').append(text);
            }
            Set<String> supportedTerms = tokens(support.toString());
            supportedTerms.retainAll(tokens(change.after()));
            if (supportedTerms.isEmpty()) fail("Tailor change is not grounded in cited candidate evidence");
            Set<String> unsupportedNumbers = matches(NUMBER, change.after());
            unsupportedNumbers.removeAll(matches(NUMBER, support.toString()));
            if (!unsupportedNumbers.isEmpty()) fail("Tailor change contains unsupported numeric claims");
        }
    }

    public void draft(AiDraftResponse response, List<EvidenceView> evidence, String channel) {
        if (Boolean.TRUE.equals(response.externallySent())) fail("Communication Agent must never send messages");
        Map<String, String> allowed = new HashMap<>();
        evidence.forEach(item -> allowed.put(item.evidenceRef(), item.text()));
        if (response.evidenceRefs() == null || response.evidenceRefs().isEmpty()) fail("Communication Draft requires evidence");
        StringBuilder support = new StringBuilder();
        for (String ref : response.evidenceRefs()) {
            String text = allowed.get(ref);
            if (text == null) fail("Communication Draft referenced unknown evidence");
            support.append(' ').append(text);
        }
        Set<String> intersection = tokens(response.content());
        intersection.retainAll(tokens(support.toString()));
        if (intersection.isEmpty()) fail("Communication Draft is not grounded in candidate evidence");
        if ("BOSS".equals(channel) && (response.charCount() < 60 || response.charCount() > 100)) {
            fail("BOSS Draft must contain 60 to 100 characters");
        }
    }

    private static Set<String> tokens(String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = TOKEN.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group().toLowerCase(Locale.ROOT));
        return result;
    }

    private static Set<String> matches(Pattern pattern, String value) {
        Set<String> result = new HashSet<>();
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        while (matcher.find()) result.add(matcher.group());
        return result;
    }

    private static void fail(String message) {
        throw new BusinessException(4226001, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
