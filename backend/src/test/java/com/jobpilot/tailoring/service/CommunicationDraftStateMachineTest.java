package com.jobpilot.tailoring.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.common.exception.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CommunicationDraftStateMachineTest {
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> transitions() {
        return CommunicationDraftStateMachine.STATUSES.stream().flatMap(from ->
                CommunicationDraftStateMachine.STATUSES.stream().map(to ->
                        org.junit.jupiter.params.provider.Arguments.of(from, to, CommunicationDraftStateMachine.allowed(from, to))));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void validatesEveryStatusPair(String from, String to, boolean allowed) {
        if (allowed) assertThatCode(() -> CommunicationDraftStateMachine.require(from, to)).doesNotThrowAnyException();
        else assertThatThrownBy(() -> CommunicationDraftStateMachine.require(from, to)).isInstanceOf(BusinessException.class);
    }
}
