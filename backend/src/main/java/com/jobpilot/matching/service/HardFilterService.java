package com.jobpilot.matching.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.matching.domain.HardFilterRuleEntity;
import com.jobpilot.matching.dto.MatchingDtos.HardFilterEvidenceView;
import com.jobpilot.matching.dto.MatchingDtos.HardFilterRuleRequest;
import com.jobpilot.matching.dto.MatchingDtos.HardFilterRuleView;
import com.jobpilot.matching.mapper.HardFilterRuleMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HardFilterService {
    private static final Map<String, Set<String>> ALLOWED_OPERATORS = Map.of(
            "GRADUATION_YEAR", Set.of("EQ","NE","GTE","LTE","IN","NOT_IN","RANGE"),
            "EDUCATION", Set.of("EQ","GTE","LTE","IN"),
            "CITY", Set.of("EQ","NE","IN","NOT_IN"),
            "EXPERIENCE_YEARS", Set.of("GTE","LTE","RANGE"),
            "JOB_TYPE", Set.of("EQ","NE","IN","NOT_IN"),
            "SALARY", Set.of("GTE","LTE","RANGE"),
            "COMPANY_BLACKLIST", Set.of("CONTAINS","IN"),
            "JOB_KEYWORD_BLACKLIST", Set.of("CONTAINS","IN"));
    private static final Map<String, Integer> EDUCATION_RANK = Map.of(
            "HIGH_SCHOOL", 1, "COLLEGE", 2, "BACHELOR", 3, "MASTER", 4, "DOCTOR", 5,
            "高中", 1, "大专", 2, "本科", 3, "硕士", 4, "博士", 5);
    private final HardFilterRuleMapper mapper;
    private final ObjectMapper objectMapper;
    private final JsonCodec json;
    private final AuditService audit;

    public HardFilterService(HardFilterRuleMapper mapper, ObjectMapper objectMapper, JsonCodec json, AuditService audit) {
        this.mapper = mapper; this.objectMapper = objectMapper; this.json = json; this.audit = audit;
    }

    @Transactional
    public HardFilterRuleView create(Long userId, HardFilterRuleRequest request) {
        validate(request);
        HardFilterRuleEntity existing = mapper.selectOne(new LambdaQueryWrapper<HardFilterRuleEntity>()
                .eq(HardFilterRuleEntity::getUserId, userId).eq(HardFilterRuleEntity::getRuleKey, request.ruleKey()).eq(HardFilterRuleEntity::getActive, true).last("LIMIT 1"));
        if (existing != null) throw new ValidationException("An active hard filter rule with this ruleKey already exists");
        int version = mapper.selectList(new LambdaQueryWrapper<HardFilterRuleEntity>()
                        .eq(HardFilterRuleEntity::getUserId, userId).eq(HardFilterRuleEntity::getRuleKey, request.ruleKey()))
                .stream().mapToInt(HardFilterRuleEntity::getVersionNo).max().orElse(0) + 1;
        HardFilterRuleEntity entity = entity(userId, request, version);
        mapper.insert(entity);
        audit.record(userId, "HARD_FILTER_RULE_CREATE", "HARD_FILTER_RULE", entity.getPublicId());
        return view(entity);
    }

    @Transactional
    public HardFilterRuleView update(Long userId, String publicId, HardFilterRuleRequest request) {
        validate(request);
        HardFilterRuleEntity current = owned(userId, publicId);
        if (!current.getRuleKey().equals(request.ruleKey())) throw new ValidationException("ruleKey is immutable across versions");
        current.setActive(false);
        mapper.updateById(current);
        HardFilterRuleEntity replacement = entity(userId, request, current.getVersionNo() + 1);
        mapper.insert(replacement);
        audit.record(userId, "HARD_FILTER_RULE_VERSION_CREATE", "HARD_FILTER_RULE", replacement.getPublicId());
        return view(replacement);
    }

    public List<HardFilterRuleView> list(Long userId) {
        return mapper.selectList(new LambdaQueryWrapper<HardFilterRuleEntity>()
                        .eq(HardFilterRuleEntity::getUserId, userId)
                        .orderByAsc(HardFilterRuleEntity::getPriority).orderByDesc(HardFilterRuleEntity::getVersionNo))
                .stream().map(this::view).toList();
    }

    public String activeRulesHash(Long userId) {
        String snapshot = mapper.selectList(new LambdaQueryWrapper<HardFilterRuleEntity>()
                        .eq(HardFilterRuleEntity::getUserId, userId)
                        .eq(HardFilterRuleEntity::getActive, true)
                        .orderByAsc(HardFilterRuleEntity::getPriority)
                        .orderByAsc(HardFilterRuleEntity::getRuleKey))
                .stream()
                .map(rule -> String.join("|", rule.getRuleKey(), String.valueOf(rule.getVersionNo()),
                        rule.getRuleType(), rule.getOperator(), rule.getOperandJson(), rule.getResultAction(),
                        rule.getPenalty().toPlainString(), String.valueOf(rule.getPriority())))
                .reduce("", (left, right) -> left + "\n" + right);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public HardFilterOutcome evaluate(Long userId, CandidateProfileEntity profile, JobEntity job, CompanyEntity company) {
        List<HardFilterRuleEntity> rules = mapper.selectList(new LambdaQueryWrapper<HardFilterRuleEntity>()
                .eq(HardFilterRuleEntity::getUserId, userId).eq(HardFilterRuleEntity::getActive, true)
                .orderByAsc(HardFilterRuleEntity::getPriority).orderByAsc(HardFilterRuleEntity::getId));
        List<HardFilterEvidenceView> evidence = new ArrayList<>();
        BigDecimal penalty = BigDecimal.ZERO;
        String result = "PASS";
        for (HardFilterRuleEntity rule : rules) {
            Evaluation evaluation = evaluateRule(rule, profile, job, company);
            if (!evaluation.violated()) continue;
            evidence.add(new HardFilterEvidenceView(rule.getPublicId(), rule.getRuleKey(), rule.getRuleType(), rule.getResultAction(),
                    evaluation.expected(), evaluation.actual(), rule.getPenalty(), evaluation.explanation()));
            if ("REJECT".equals(rule.getResultAction())) result = "REJECT";
            else if ("DOWNGRADE".equals(rule.getResultAction()) && !"REJECT".equals(result)) {
                result = "DOWNGRADE";
                penalty = penalty.add(rule.getPenalty());
            }
        }
        return new HardFilterOutcome(result, penalty.min(new BigDecimal("100.00")), evidence);
    }

    private Evaluation evaluateRule(HardFilterRuleEntity rule, CandidateProfileEntity profile, JobEntity job, CompanyEntity company) {
        JsonNode operand = json.readNode(rule.getOperandJson());
        return switch (rule.getRuleType()) {
            case "EXPERIENCE_YEARS" -> compareNumber(rule, profile.getYearsOfExperience(), sourceJob(operand) ? job.getExperienceMinYears() : number(operand), "candidate experience does not satisfy the required years");
            case "EDUCATION" -> compareEducation(rule, profile.getHighestEducation(), sourceJob(operand) ? job.getEducation() : text(operand), "candidate education does not satisfy the required level");
            case "GRADUATION_YEAR" -> compareNumber(rule, decimal(profile.getGraduationYear()), sourceJob(operand) ? decimal(job.getGraduateYear()) : number(operand), "candidate graduation year does not satisfy the rule");
            case "CITY" -> compareCity(rule, profile, job, operand);
            case "JOB_TYPE" -> compareText(rule, job.getJobType(), operand, "job type violates the configured rule");
            case "SALARY" -> compareSalary(rule, profile, job, operand);
            case "COMPANY_BLACKLIST" -> contains(rule, company == null ? null : company.getDisplayName(), operand, "company matches the configured blacklist");
            case "JOB_KEYWORD_BLACKLIST" -> contains(rule, (job.getTitle() + " " + job.getDescriptionClean()), operand, "job content matches a configured blacklist keyword");
            default -> throw new ValidationException("Unsupported hard filter rule type");
        };
    }

    private Evaluation compareCity(HardFilterRuleEntity rule, CandidateProfileEntity profile, JobEntity job, JsonNode operand) {
        List<String> expected = sourceProfile(operand) ? json.readStringList(profile.getTargetCitiesJson()) : strings(operand);
        boolean contains = expected.stream().anyMatch(city -> equalsIgnoreCase(city, job.getCity()));
        boolean violation = switch (rule.getOperator()) { case "IN", "EQ" -> !contains; case "NOT_IN", "NE" -> contains; default -> true; };
        return evaluation(violation, expected, job.getCity(), "job city does not satisfy candidate target cities");
    }

    private Evaluation compareSalary(HardFilterRuleEntity rule, CandidateProfileEntity profile, JobEntity job, JsonNode operand) {
        if (sourceProfile(operand)) {
            BigDecimal targetMin = profile.getTargetSalaryMin(), targetMax = profile.getTargetSalaryMax();
            boolean unknown = job.getSalaryMin() == null && job.getSalaryMax() == null;
            boolean overlap = !unknown && (targetMin == null || job.getSalaryMax() == null || job.getSalaryMax().compareTo(targetMin) >= 0)
                    && (targetMax == null || job.getSalaryMin() == null || job.getSalaryMin().compareTo(targetMax) <= 0);
            Map<String, Object> expected = new HashMap<>(); expected.put("min", targetMin); expected.put("max", targetMax);
            Map<String, Object> actual = new HashMap<>(); actual.put("min", job.getSalaryMin()); actual.put("max", job.getSalaryMax());
            return evaluation(!overlap, expected, actual, "job salary does not overlap candidate target salary");
        }
        return compareNumber(rule, job.getSalaryMin(), number(operand), "job salary violates the configured rule");
    }

    private Evaluation compareEducation(HardFilterRuleEntity rule, String actualText, String expectedText, String explanation) {
        if ("IN".equals(rule.getOperator())) {
            List<String> expectedValues = strings(json.readNode(rule.getOperandJson()));
            if (expectedValues.isEmpty()) return evaluation(false, expectedValues, actualText, explanation);
            boolean matched = expectedValues.stream().anyMatch(value -> equalsIgnoreCase(value, actualText));
            return evaluation(!matched, expectedValues, actualText, explanation);
        }
        Integer actual = rank(actualText), expected = rank(expectedText);
        if (expected == null) return evaluation(false, expectedText, actualText, explanation);
        boolean violation = actual == null || switch (rule.getOperator()) {
            case "GTE" -> actual < expected; case "LTE" -> actual > expected; case "EQ" -> !actual.equals(expected);
            default -> true;
        };
        return evaluation(violation, expectedText, actualText, explanation);
    }

    private Evaluation compareNumber(HardFilterRuleEntity rule, BigDecimal actual, BigDecimal expected, String explanation) {
        if (List.of("RANGE", "IN", "NOT_IN").contains(rule.getOperator())) {
            List<BigDecimal> values = numbers(json.readNode(rule.getOperandJson()));
            if (values.isEmpty()) return evaluation(false, values, actual, explanation);
            boolean violation = actual == null || switch (rule.getOperator()) {
                case "RANGE" -> values.size() != 2 || actual.compareTo(values.get(0)) < 0 || actual.compareTo(values.get(1)) > 0;
                case "IN" -> values.stream().noneMatch(value -> value.compareTo(actual) == 0);
                case "NOT_IN" -> values.stream().anyMatch(value -> value.compareTo(actual) == 0);
                default -> true;
            };
            return evaluation(violation, values, actual, explanation);
        }
        if (expected == null) return evaluation(false, null, actual, explanation);
        boolean violation = actual == null || switch (rule.getOperator()) {
            case "GTE" -> actual.compareTo(expected) < 0; case "LTE" -> actual.compareTo(expected) > 0;
            case "EQ" -> actual.compareTo(expected) != 0; case "NE" -> actual.compareTo(expected) == 0;
            default -> true;
        };
        return evaluation(violation, expected, actual, explanation);
    }

    private Evaluation compareText(HardFilterRuleEntity rule, String actual, JsonNode operand, String explanation) {
        List<String> expected = strings(operand);
        String single = text(operand);
        boolean equal = equalsIgnoreCase(actual, single);
        boolean included = expected.stream().anyMatch(value -> equalsIgnoreCase(value, actual));
        boolean violation = switch (rule.getOperator()) {
            case "EQ" -> !equal; case "NE" -> equal; case "IN" -> !included; case "NOT_IN" -> included; default -> true;
        };
        return evaluation(violation, expected.isEmpty() ? single : expected, actual, explanation);
    }

    private Evaluation contains(HardFilterRuleEntity rule, String actual, JsonNode operand, String explanation) {
        List<String> values = strings(operand);
        String haystack = actual == null ? "" : actual.toLowerCase(Locale.ROOT);
        boolean matched = values.stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(haystack::contains);
        return evaluation(matched, values, actual, explanation);
    }

    private void validate(HardFilterRuleRequest request) {
        Set<String> operators = ALLOWED_OPERATORS.get(request.ruleType());
        if (operators == null || !operators.contains(request.operator())) throw new ValidationException("operator is not allowed for ruleType");
        if (request.operand().isNull() || (!sourceJob(request.operand()) && !sourceProfile(request.operand()) && number(request.operand()) == null && text(request.operand()) == null && strings(request.operand()).isEmpty())) {
            throw new ValidationException("operand must contain a value, values or supported source");
        }
        if ("WARN".equals(request.resultAction()) && request.penalty().signum() != 0) throw new ValidationException("WARN rules cannot apply a penalty");
        if ("REJECT".equals(request.resultAction()) && request.penalty().signum() != 0) throw new ValidationException("REJECT rules do not use a penalty");
    }

    private HardFilterRuleEntity entity(Long userId, HardFilterRuleRequest request, int version) {
        HardFilterRuleEntity entity = new HardFilterRuleEntity();
        entity.setUserId(userId); entity.setRuleKey(request.ruleKey()); entity.setName(request.name().trim());
        entity.setRuleType(request.ruleType()); entity.setOperator(request.operator()); entity.setOperandJson(json.writeNode(request.operand()));
        entity.setResultAction(request.resultAction()); entity.setPenalty(request.penalty()); entity.setPriority(request.priority());
        entity.setActive(!Boolean.FALSE.equals(request.active())); entity.setVersionNo(version);
        return entity;
    }

    private HardFilterRuleView view(HardFilterRuleEntity entity) {
        return new HardFilterRuleView(entity.getPublicId(), entity.getRuleKey(), entity.getName(), entity.getRuleType(), entity.getOperator(),
                json.readNode(entity.getOperandJson()), entity.getResultAction(), entity.getPenalty(), entity.getPriority(),
                Boolean.TRUE.equals(entity.getActive()), entity.getVersionNo(), entity.getCreatedAt());
    }

    private HardFilterRuleEntity owned(Long userId, String publicId) {
        HardFilterRuleEntity entity = mapper.selectOne(new LambdaQueryWrapper<HardFilterRuleEntity>()
                .eq(HardFilterRuleEntity::getUserId, userId).eq(HardFilterRuleEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Hard filter rule");
        return entity;
    }

    private boolean sourceJob(JsonNode node) { return node.isObject() && "JOB_REQUIREMENT".equals(node.path("source").asText()); }
    private boolean sourceProfile(JsonNode node) { return node.isObject() && "PROFILE_TARGET".equals(node.path("source").asText()); }
    private BigDecimal number(JsonNode node) { JsonNode value=node.isObject()?node.get("value"):node; return value!=null&&value.isNumber()?value.decimalValue():null; }
    private String text(JsonNode node) { JsonNode value=node.isObject()?node.get("value"):node; return value!=null&&value.isTextual()?value.asText():null; }
    private List<String> strings(JsonNode node) { JsonNode values=node.isObject()?node.get("values"):node; if(values==null||!values.isArray())return List.of(); List<String> result=new ArrayList<>(); values.forEach(value->{if(value.isTextual())result.add(value.asText());}); return result; }
    private List<BigDecimal> numbers(JsonNode node) { JsonNode values=node.isObject()?node.get("values"):node; if(values==null||!values.isArray())return List.of(); List<BigDecimal> result=new ArrayList<>(); values.forEach(value->{if(value.isNumber())result.add(value.decimalValue());}); return result; }
    private BigDecimal decimal(Integer value) { return value == null ? null : BigDecimal.valueOf(value); }
    private Integer rank(String value) { return value == null ? null : EDUCATION_RANK.get(value.trim().toUpperCase(Locale.ROOT)); }
    private boolean equalsIgnoreCase(String left, String right) { return left != null && right != null && left.trim().equalsIgnoreCase(right.trim()); }
    private Evaluation evaluation(boolean violated, Object expected, Object actual, String explanation) { return new Evaluation(violated, tree(expected), tree(actual), explanation); }
    private JsonNode tree(Object value) { return value == null ? NullNode.getInstance() : objectMapper.valueToTree(value); }

    public record HardFilterOutcome(String result, BigDecimal penalty, List<HardFilterEvidenceView> evidence) { }
    private record Evaluation(boolean violated, JsonNode expected, JsonNode actual, String explanation) { }
}
