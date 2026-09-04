package com.jobpilot.application.service;

import com.jobpilot.common.exception.BusinessException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class ApplicationStateMachine {
    public static final Set<String> STATUSES = Set.of("APPLIED", "VIEWED", "REPLIED", "WRITTEN_TEST",
            "INTERVIEW_1", "INTERVIEW_2", "INTERVIEW_3", "HR_INTERVIEW", "OFFER",
            "REJECTED", "WITHDRAWN", "CLOSED");
    public static final Set<String> TERMINAL = Set.of("REJECTED", "WITHDRAWN", "CLOSED");
    private static final Map<String, Set<String>> TRANSITIONS = transitions();

    private ApplicationStateMachine() { }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void require(String from, String to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(4095201,
                    "Illegal application transition: " + from + " -> " + to, HttpStatus.CONFLICT);
        }
    }

    public static Map<String, Set<String>> allowedTransitions() {
        return TRANSITIONS;
    }

    private static Map<String, Set<String>> transitions() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        result.put("APPLIED", Set.of("VIEWED", "REPLIED", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("VIEWED", Set.of("REPLIED", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("REPLIED", Set.of("WRITTEN_TEST", "INTERVIEW_1", "HR_INTERVIEW", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("WRITTEN_TEST", Set.of("INTERVIEW_1", "HR_INTERVIEW", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("INTERVIEW_1", Set.of("INTERVIEW_2", "HR_INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("INTERVIEW_2", Set.of("INTERVIEW_3", "HR_INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("INTERVIEW_3", Set.of("HR_INTERVIEW", "OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("HR_INTERVIEW", Set.of("OFFER", "REJECTED", "WITHDRAWN", "CLOSED"));
        result.put("OFFER", Set.of("WITHDRAWN", "CLOSED"));
        return Map.copyOf(result);
    }
}
