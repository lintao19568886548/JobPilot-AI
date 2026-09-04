package com.jobpilot.automation.service;

import com.jobpilot.automation.domain.AutomationRuleEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AutomationCenterScheduler {
    private static final Logger log = LoggerFactory.getLogger(AutomationCenterScheduler.class);
    private final AutomationCenterService center;

    public AutomationCenterScheduler(AutomationCenterService center) { this.center = center; }

    @Scheduled(fixedDelayString = "${jobpilot.automation-center.poll-interval-ms:60000}")
    public void poll() {
        for (AutomationRuleEntity rule : center.dueRules()) {
            try {
                center.runScheduled(rule);
            } catch (RuntimeException exception) {
                log.warn("Safe automation rule {} could not be started: {}", rule.getPublicId(), exception.getClass().getSimpleName());
            }
        }
    }
}
