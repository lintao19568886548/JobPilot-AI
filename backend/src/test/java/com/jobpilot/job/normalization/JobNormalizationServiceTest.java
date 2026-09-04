package com.jobpilot.job.normalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobNormalizationServiceTest {
    private final JobNormalizationService service = new JobNormalizationService();

    @Test void companySuffixAndWhitespaceAreNormalized() {
        assertThat(service.companyName("  示例科技有限公司  ")).isEqualTo("示例科技");
    }

    @Test void traditionalTitleCharactersAreCanonicalized() {
        assertThat(service.title("Java 開發工程師")).isEqualTo("java 开发工程师");
    }

    @Test void skillAliasesIgnoreSeparatorsAndWidth() {
        assertThat(service.alias("Ｓｐｒｉｎｇ-Boot_3")).isEqualTo("springboot3");
    }

    @Test void canonicalKeyIsStableAcrossCompanySuffixes() {
        assertThat(service.canonicalKey("Acme Ltd.", "Java Engineer", "Shanghai"))
                .isEqualTo(service.canonicalKey("acme", " java engineer ", "shanghai"));
    }

    @Test void fingerprintsChangeWhenDescriptionChanges() {
        assertThat(service.fingerprint("Acme", "Java", "上海", "负责 API"))
                .isNotEqualTo(service.fingerprint("Acme", "Java", "上海", "负责缓存"));
    }

    @Test void normalizedContentHashIgnoresWhitespaceRuns() {
        assertThat(service.contentHash("Java  Redis\nKafka"))
                .isEqualTo(service.contentHash("Java Redis Kafka"));
    }

    @Test void blankRequiredValueIsRejected() {
        assertThatThrownBy(() -> service.companyName(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
