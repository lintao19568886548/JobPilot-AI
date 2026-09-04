package com.jobpilot.interview.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jobpilot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class InterviewPolicyTest {
    @Test void scheduledInterviewCanStart() { assertDoesNotThrow(() -> InterviewPolicy.interviewTransition("SCHEDULED", "IN_PROGRESS")); }
    @Test void completedInterviewCannotRestart() { assertThrows(BusinessException.class, () -> InterviewPolicy.interviewTransition("COMPLETED", "IN_PROGRESS")); }
    @Test void cancelledInterviewCanBeRescheduled() { assertDoesNotThrow(() -> InterviewPolicy.interviewTransition("CANCELLED", "SCHEDULED")); }
    @Test void plannedRoundCanComplete() { assertDoesNotThrow(() -> InterviewPolicy.roundTransition("PLANNED", "COMPLETED")); }
    @Test void completedRoundCannotReturnToPlanned() { assertThrows(BusinessException.class, () -> InterviewPolicy.roundTransition("COMPLETED", "PLANNED")); }
}
