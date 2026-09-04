package com.jobpilot.offer.service;

import com.jobpilot.common.exception.BusinessException;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

public final class OfferPolicy {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("RECEIVED", "WITHDRAWN"),
            "RECEIVED", Set.of("CONSIDERING", "ACCEPTED", "DECLINED", "EXPIRED", "WITHDRAWN"),
            "CONSIDERING", Set.of("ACCEPTED", "DECLINED", "EXPIRED", "WITHDRAWN"),
            "ACCEPTED", Set.of(), "DECLINED", Set.of(), "EXPIRED", Set.of(), "WITHDRAWN", Set.of());

    private OfferPolicy() { }

    public static void transition(String from, String to) {
        if (from.equals(to)) return;
        if (!TRANSITIONS.getOrDefault(from, Set.of()).contains(to)) {
            throw new BusinessException(4099001, "Invalid Offer status transition", HttpStatus.CONFLICT);
        }
    }

    public static boolean terminal(String status) {
        return Set.of("ACCEPTED", "DECLINED", "EXPIRED", "WITHDRAWN").contains(status);
    }
}
