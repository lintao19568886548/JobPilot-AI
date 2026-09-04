package com.jobpilot.automation.service;

import com.jobpilot.automation.config.AutomationProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class AutomationTaskTokenService {
    private final AutomationProperties properties;

    public AutomationTaskTokenService(AutomationProperties properties) { this.properties = properties; }

    public String create(String taskId, Instant expiresAt, String queueApprovalId, String policyMode) {
        if (properties.getWorkerToken() == null || properties.getWorkerToken().length() < 24) {
            throw new IllegalStateException("Automation worker token must contain at least 24 characters");
        }
        String claims = String.join("|", taskId, String.valueOf(expiresAt.getEpochSecond()),
                queueApprovalId, policyMode);
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        return "jpt_" + encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(encoded));
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getWorkerToken().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("Unable to sign automation task token", exception);
        }
    }
}
