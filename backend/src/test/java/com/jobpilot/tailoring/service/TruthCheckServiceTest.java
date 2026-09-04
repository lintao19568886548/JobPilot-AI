package com.jobpilot.tailoring.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.tailoring.dto.TailoringDtos.AiDraftResponse;
import com.jobpilot.tailoring.dto.TailoringDtos.AiTailorChange;
import com.jobpilot.tailoring.dto.TailoringDtos.EvidenceView;
import java.util.List;
import org.junit.jupiter.api.Test;

class TruthCheckServiceTest {
    private final TruthCheckService service = new TruthCheckService();
    private final EvidenceView java = new EvidenceView("ev-java", "skill", "SKILL", "CANDIDATE_SKILL",
            "source", "skill", null, "Java；熟练度 95", "hash", 1, null);

    @Test
    void acceptsGroundedTailorChange() {
        var change = new AiTailorChange("SKILLS", "REORDER", null, "核心技能 Java；熟练度 95",
                "岗位相关", List.of("ev-java"));
        assertThatCode(() -> service.tailor(List.of(change), List.of(java))).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnknownEvidenceReference() {
        var change = new AiTailorChange("SKILLS", "REWRITE", null, "Java", "岗位相关", List.of("other-user"));
        assertThatThrownBy(() -> service.tailor(List.of(change), List.of(java)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("unknown");
    }

    @Test
    void rejectsUnsupportedNumericClaim() {
        var change = new AiTailorChange("SKILLS", "REWRITE", null, "Java 熟练度 100", "夸大", List.of("ev-java"));
        assertThatThrownBy(() -> service.tailor(List.of(change), List.of(java)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("numeric");
    }

    @Test
    void rejectsDraftThatClaimsExternalSend() {
        var response = new AiDraftResponse("communication-draft-response-v1", "DRAFT", "RULES_ONLY",
                "communication/v1", null, "VERIFIED", "Java", 60, List.of("ev-java"), true, 1);
        assertThatThrownBy(() -> service.draft(response, List.of(java), "BOSS"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("never send");
    }

    @Test
    void enforcesBossLength() {
        var response = new AiDraftResponse("communication-draft-response-v1", "DRAFT", "RULES_ONLY",
                "communication/v1", null, "VERIFIED", "Java", 20, List.of("ev-java"), false, 1);
        assertThatThrownBy(() -> service.draft(response, List.of(java), "BOSS"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("60 to 100");
    }
}
