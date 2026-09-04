package com.jobpilot.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.common.exception.BusinessException;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class QueueStateMachineTest {
    private static final Set<String> STATUSES = Set.of(
            "WAITING", "READY", "NEED_REVIEW", "APPROVED", "PREPARED",
            "SUCCESS", "FAILED", "SKIPPED", "BLOCKED");

    @ParameterizedTest(name = "{0} -> {1} legal={2}")
    @MethodSource("allPairs")
    void validatesEveryQueuePair(String from, String to, boolean legal) {
        if (legal) {
            assertThatCode(() -> QueueStateMachine.require(from, to)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> QueueStateMachine.require(from, to))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Illegal queue transition");
        }
    }

    static Stream<Arguments> allPairs() {
        return STATUSES.stream().flatMap(from -> STATUSES.stream()
                .map(to -> Arguments.of(from, to, isLegal(from, to))));
    }

    private static boolean isLegal(String from, String to) {
        return switch (from) {
            case "WAITING" -> Set.of("READY", "NEED_REVIEW", "SKIPPED", "BLOCKED").contains(to);
            case "READY" -> Set.of("NEED_REVIEW", "APPROVED", "SKIPPED", "BLOCKED").contains(to);
            case "NEED_REVIEW" -> Set.of("READY", "APPROVED", "SKIPPED", "BLOCKED").contains(to);
            case "APPROVED" -> Set.of("PREPARED", "SUCCESS", "FAILED", "SKIPPED", "BLOCKED").contains(to);
            case "PREPARED" -> Set.of("SUCCESS", "FAILED", "SKIPPED", "BLOCKED").contains(to);
            case "BLOCKED" -> Set.of("READY", "NEED_REVIEW", "SKIPPED").contains(to);
            default -> false;
        };
    }
}
