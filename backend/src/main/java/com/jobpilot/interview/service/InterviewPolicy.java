package com.jobpilot.interview.service;

import com.jobpilot.common.exception.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class InterviewPolicy {
    private static final Map<String, Set<String>> INTERVIEW_TRANSITIONS = Map.of(
            "SCHEDULED", Set.of("IN_PROGRESS", "COMPLETED", "CANCELLED", "NO_SHOW"),
            "IN_PROGRESS", Set.of("COMPLETED", "CANCELLED", "NO_SHOW"),
            "COMPLETED", Set.of(), "CANCELLED", Set.of("SCHEDULED"), "NO_SHOW", Set.of("SCHEDULED"));
    private static final Map<String, Set<String>> ROUND_TRANSITIONS = Map.of(
            "PLANNED", Set.of("COMPLETED", "CANCELLED"),
            "COMPLETED", Set.of(), "CANCELLED", Set.of("PLANNED"));

    private InterviewPolicy() { }

    public static void interviewTransition(String from, String to) {
        transition("Interview", INTERVIEW_TRANSITIONS, from, to);
    }

    public static void roundTransition(String from, String to) {
        transition("Interview Round", ROUND_TRANSITIONS, from, to);
    }

    private static void transition(String resource, Map<String, Set<String>> transitions, String from, String to) {
        if (to == null || from.equals(to)) return;
        if (!transitions.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(4098001, resource + " cannot transition from " + from + " to " + to, HttpStatus.CONFLICT);
        }
    }
}
