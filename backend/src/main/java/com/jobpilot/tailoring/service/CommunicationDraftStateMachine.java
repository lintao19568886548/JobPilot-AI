package com.jobpilot.tailoring.service;

import com.jobpilot.common.exception.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class CommunicationDraftStateMachine {
    public static final Set<String> STATUSES = Set.of("DRAFT", "APPROVED", "USED", "ARCHIVED");
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "DRAFT", Set.of("APPROVED", "ARCHIVED"),
            "APPROVED", Set.of("USED", "ARCHIVED"),
            "USED", Set.of("ARCHIVED"),
            "ARCHIVED", Set.of());

    private CommunicationDraftStateMachine() { }
    public static void require(String from, String to) {
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(4096106, "Communication Draft transition not allowed: " + from + " -> " + to,
                    HttpStatus.CONFLICT);
        }
    }
    public static boolean allowed(String from, String to) { return ALLOWED.getOrDefault(from, Set.of()).contains(to); }
}
