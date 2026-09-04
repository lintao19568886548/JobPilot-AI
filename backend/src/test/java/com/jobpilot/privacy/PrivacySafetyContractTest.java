package com.jobpilot.privacy;

import static org.junit.jupiter.api.Assertions.*;
import com.jobpilot.privacy.service.PrivacyService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PrivacySafetyContractTest {
    @Test void usesAnExactNonTrivialConfirmationPhrase(){assertEquals("DELETE MY JOBPILOT DATA",PrivacyService.CONFIRMATION_PHRASE);}
    @Test void exportNeverSelectsAuthenticationSecrets() throws Exception {String source=Files.readString(Path.of("src/main/java/com/jobpilot/privacy/service/PrivacyService.java"));assertFalse(source.contains("SELECT * FROM users"));assertFalse(source.contains("password_hash"));assertFalse(source.contains("token_hash"));}
}
