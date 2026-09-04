package com.jobpilot.job.normalization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class JobNormalizationService {
    private static final List<String> COMPANY_SUFFIXES = List.of(
            "股份有限公司", "有限责任公司", "有限公司", "corp.", "ltd.", "公司", "corp", "ltd", "inc.", "inc", "llc");

    public String companyName(String value) {
        String normalized = basic(value);
        for (String suffix : COMPANY_SUFFIXES) {
            String token = suffix.toLowerCase(Locale.ROOT);
            if (normalized.endsWith(token) && normalized.length() > token.length()) {
                normalized = normalized.substring(0, normalized.length() - token.length()).trim();
                break;
            }
        }
        return normalized;
    }

    public String title(String value) {
        return basic(value).replace("工程師", "工程师").replace("開發", "开发");
    }

    public String alias(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .trim().toLowerCase(Locale.ROOT)
                .replaceAll("[\\s._\\-]+", "");
    }

    public String canonicalKey(String company, String title, String city) {
        return sha256(companyName(company) + "|" + title(title) + "|" + basicNullable(city));
    }

    public String fingerprint(String company, String title, String city, String description) {
        return sha256(canonicalKey(company, title, city) + "|" + normalizeText(description));
    }

    public String contentHash(String value) { return sha256(normalizeText(value)); }
    public String urlHash(String value) { return value == null || value.isBlank() ? null : sha256(value.trim()); }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String basic(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("value must not be blank");
        return basicNullable(value);
    }

    private String basicNullable(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String normalizeText(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC).trim().replaceAll("\\s+", " ");
    }
}
