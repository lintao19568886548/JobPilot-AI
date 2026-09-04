package com.jobpilot.application.service;

import com.jobpilot.common.exception.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class QueueStateMachine {
    public static final Set<String> TERMINAL = Set.of("SUCCESS", "FAILED", "SKIPPED");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "WAITING", Set.of("READY", "NEED_REVIEW", "SKIPPED", "BLOCKED"),
            "READY", Set.of("NEED_REVIEW", "APPROVED", "SKIPPED", "BLOCKED"),
            "NEED_REVIEW", Set.of("READY", "APPROVED", "SKIPPED", "BLOCKED"),
            "APPROVED", Set.of("PREPARED", "SUCCESS", "FAILED", "SKIPPED", "BLOCKED"),
            "PREPARED", Set.of("SUCCESS", "FAILED", "SKIPPED", "BLOCKED"),
            "BLOCKED", Set.of("READY", "NEED_REVIEW", "SKIPPED"));

    private QueueStateMachine() { }

    public static void require(String from, String to) {
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(4095101,
                    "Illegal queue transition: " + from + " -> " + to, HttpStatus.CONFLICT);
        }
    }
}
