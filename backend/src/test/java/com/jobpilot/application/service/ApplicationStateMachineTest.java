package com.jobpilot.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.common.exception.BusinessException;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApplicationStateMachineTest {
    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("allStatusPairs")
    void validatesEveryStatusPair(String from, String to) {
        boolean expected = ApplicationStateMachine.allowedTransitions()
                .getOrDefault(from, Set.of()).contains(to);
        assertThat(ApplicationStateMachine.canTransition(from, to)).isEqualTo(expected);
        if (expected) {
            assertThatCode(() -> ApplicationStateMachine.require(from, to)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ApplicationStateMachine.require(from, to))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("Illegal application transition");
        }
    }

    static Stream<Arguments> allStatusPairs() {
        return ApplicationStateMachine.STATUSES.stream()
                .flatMap(from -> ApplicationStateMachine.STATUSES.stream().map(to -> Arguments.of(from, to)));
    }
}
