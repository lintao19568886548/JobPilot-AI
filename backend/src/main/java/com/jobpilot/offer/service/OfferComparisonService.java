package com.jobpilot.offer.service;

import static com.jobpilot.offer.dto.OfferDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.offer.domain.OfferBenefitEntity;
import com.jobpilot.offer.domain.OfferComparisonEntity;
import com.jobpilot.offer.domain.OfferComparisonItemEntity;
import com.jobpilot.offer.domain.OfferEntity;
import com.jobpilot.offer.mapper.OfferBenefitMapper;
import com.jobpilot.offer.mapper.OfferComparisonItemMapper;
import com.jobpilot.offer.mapper.OfferComparisonMapper;
import com.jobpilot.offer.repository.OfferOwnershipRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferComparisonService {
    public static final List<String> DIMENSIONS = List.of("CASH", "VARIABLE_BONUS", "BENEFITS", "GROWTH", "WORK_LIFE", "STABILITY", "LOCATION", "PREFERENCE");
    private static final Set<String> SUBJECTIVE = Set.of("GROWTH", "WORK_LIFE", "STABILITY", "LOCATION", "PREFERENCE");
    private final OfferComparisonMapper comparisons; private final OfferComparisonItemMapper items;
    private final OfferBenefitMapper benefits; private final OfferOwnershipRepository ownership;
    private final ObjectMapper objectMapper; private final JsonCodec json; private final AuditService audit;

    public OfferComparisonService(OfferComparisonMapper comparisons, OfferComparisonItemMapper items,
                                  OfferBenefitMapper benefits, OfferOwnershipRepository ownership,
                                  ObjectMapper objectMapper, JsonCodec json, AuditService audit) {
        this.comparisons = comparisons; this.items = items; this.benefits = benefits; this.ownership = ownership;
        this.objectMapper = objectMapper; this.json = json; this.audit = audit;
    }

    public List<ComparisonView> list(Long userId) {
        return comparisons.selectList(new LambdaQueryWrapper<OfferComparisonEntity>()
                        .eq(OfferComparisonEntity::getUserId, userId)
                        .orderByDesc(OfferComparisonEntity::getComparisonVersion).orderByDesc(OfferComparisonEntity::getId))
                .stream().map(this::view).toList();
    }

    public ComparisonView get(Long userId, String publicId) {
        OfferComparisonEntity entity = comparisons.selectOne(new LambdaQueryWrapper<OfferComparisonEntity>()
                .eq(OfferComparisonEntity::getUserId, userId).eq(OfferComparisonEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Offer Comparison");
        return view(entity);
    }

    @Transactional
    public ComparisonView create(Long userId, String idempotencyKey, ComparisonRequest request) {
        validateKey(idempotencyKey); validateWeights(request.weights());
        List<String> publicIds = new ArrayList<>(new LinkedHashSet<>(request.offerIds()));
        if (publicIds.size() != request.offerIds().size()) throw new ValidationException("offerIds must be unique");
        List<OfferEntity> offers = publicIds.stream().map(id -> ownership.offer(userId, id)).toList();
        validateRatings(publicIds, request.ratings());
        List<Map<String, Object>> snapshots = offers.stream().map(offer -> snapshot(offer, benefitEntities(offer.getId()))).toList();
        Map<String, Object> input = new LinkedHashMap<>(); input.put("name", request.name().trim()); input.put("offerIds", publicIds); input.put("weights", orderedWeights(request.weights()));
        input.put("ratings", orderedRatings(request.ratings())); input.put("offers", snapshots);
        String inputJson = json.write(input); String hash = sha256(inputJson);
        OfferComparisonEntity existing = byKey(userId, idempotencyKey);
        if (existing != null) return sameHash(existing, hash);

        boolean comparable = offers.stream().map(OfferEntity::getCurrency).distinct().count() == 1;
        String currencyGroup = comparable ? offers.get(0).getCurrency() : "MULTI_CURRENCY";
        Map<Long, BigDecimal> annual = values(offers, OfferMoney::guaranteedAnnualCash);
        Map<Long, BigDecimal> variable = values(offers, o -> o.getVariableBonusMax());
        Map<Long, BigDecimal> benefitTotals = values(offers, this::benefitTotal);
        BigDecimal maxCash = maximum(annual); BigDecimal maxVariable = maximum(variable); BigDecimal maxBenefits = maximum(benefitTotals);
        List<Ranked> ranked = new ArrayList<>();
        for (OfferEntity offer : offers) {
            List<DimensionView> dimensions = new ArrayList<>();
            add(dimensions, "CASH", comparable ? annual.get(offer.getId()) : null, comparable ? maxCash : null, request.weights(), comparable ? "Guaranteed annual cash" : "Unknown: currencies differ and no exchange rate was supplied");
            add(dimensions, "VARIABLE_BONUS", comparable ? variable.get(offer.getId()) : null, comparable ? maxVariable : null, request.weights(), comparable ? "Maximum stated variable bonus" : "Unknown: currencies differ");
            add(dimensions, "BENEFITS", comparable ? benefitTotals.get(offer.getId()) : null, comparable ? maxBenefits : null, request.weights(), comparable ? "Only explicitly quantified benefits" : "Unknown: currencies differ");
            for (String key : SUBJECTIVE) {
                BigDecimal rating = rating(request.ratings(), offer.getPublicId(), key);
                addScore(dimensions, key, rating, request.weights().get(key), rating == null ? "Unknown: no user rating supplied" : "User-supplied rating");
            }
            BigDecimal knownWeight = dimensions.stream().filter(d -> !d.unknown()).map(DimensionView::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal contribution = dimensions.stream().filter(d -> !d.unknown()).map(DimensionView::contribution).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal overall = knownWeight.signum() == 0 ? BigDecimal.ZERO
                    : contribution.multiply(new BigDecimal("100")).divide(knownWeight, 2, RoundingMode.HALF_UP);
            ranked.add(new Ranked(offer, overall, dimensions, "仅使用已知维度评分；未知维度不会计入分母。"));
        }
        ranked.sort(Comparator.comparing(Ranked::score).reversed().thenComparing(v -> v.offer().getPublicId()));
        OfferComparisonEntity entity = new OfferComparisonEntity(); entity.setUserId(userId); entity.setComparisonVersion(nextVersion(userId));
        entity.setName(request.name().trim()); entity.setOfferIdsJson(json.write(publicIds)); entity.setWeightsJson(json.write(orderedWeights(request.weights())));
        entity.setInputSnapshotJson(inputJson); entity.setInputHash(hash); entity.setIdempotencyKey(idempotencyKey.trim());
        entity.setCurrencyGroup(currencyGroup); entity.setCashComparable(comparable);
        entity.setSummaryText(comparable ? "现金维度统一使用 " + currencyGroup + " 比较。" : "Offer 使用不同币种，现金维度按未知处理；系统不会虚构汇率。");
        try { comparisons.insert(entity); }
        catch (DataIntegrityViolationException exception) {
            OfferComparisonEntity concurrent = byKey(userId, idempotencyKey);
            if (concurrent != null) return sameHash(concurrent, hash); throw exception;
        }
        for (int index = 0; index < ranked.size(); index++) {
            Ranked value = ranked.get(index); OfferComparisonItemEntity item = new OfferComparisonItemEntity();
            item.setUserId(userId); item.setComparisonId(entity.getId()); item.setOfferId(value.offer().getId()); item.setRankNo(index + 1);
            item.setGuaranteedAnnualCash(OfferMoney.guaranteedAnnualCash(value.offer())); item.setPotentialAnnualCash(OfferMoney.potentialAnnualCash(value.offer()));
            item.setOverallScore(value.score()); item.setDimensionsJson(json.write(value.dimensions())); item.setExplanationText(value.explanation()); items.insert(item);
        }
        audit.record(userId, "OFFER_COMPARISON_CREATE", "OFFER_COMPARISON", entity.getPublicId());
        return view(entity);
    }

    static void validateWeights(Map<String, BigDecimal> weights) {
        if (weights == null || !weights.keySet().equals(new LinkedHashSet<>(DIMENSIONS))) throw new ValidationException("weights must contain exactly: " + String.join(", ", DIMENSIONS));
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal value : weights.values()) {
            if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) throw new ValidationException("weights must be within 0..100");
            total = total.add(value);
        }
        if (total.compareTo(new BigDecimal("100")) != 0) throw new ValidationException("weights must total exactly 100");
    }

    private void validateRatings(List<String> offerIds, Map<String, Map<String, BigDecimal>> ratings) {
        if (ratings == null) return;
        if (!offerIds.containsAll(ratings.keySet())) throw new ValidationException("ratings contain an Offer outside offerIds");
        for (Map<String, BigDecimal> values : ratings.values()) for (Map.Entry<String, BigDecimal> entry : values.entrySet()) {
            if (!SUBJECTIVE.contains(entry.getKey())) throw new ValidationException("Only subjective dimensions accept ratings");
            if (entry.getValue() == null || entry.getValue().signum() < 0 || entry.getValue().compareTo(new BigDecimal("100")) > 0) throw new ValidationException("ratings must be within 0..100");
        }
    }

    private ComparisonView view(OfferComparisonEntity entity) {
        List<Map<String, Object>> snapshots = read(entity.getInputSnapshotJson(), new TypeReference<Map<String, Object>>() {}).get("offers") instanceof List<?> list
                ? castMaps(list) : List.of();
        Map<Long, Map<String, Object>> byInternalId = new LinkedHashMap<>();
        for (Map<String, Object> snapshot : snapshots) byInternalId.put(Long.valueOf(String.valueOf(snapshot.get("internalId"))), snapshot);
        List<ComparisonItemView> itemViews = items.selectList(new LambdaQueryWrapper<OfferComparisonItemEntity>()
                        .eq(OfferComparisonItemEntity::getComparisonId, entity.getId()).orderByAsc(OfferComparisonItemEntity::getRankNo))
                .stream().map(item -> {
                    Map<String, Object> snapshot = byInternalId.getOrDefault(item.getOfferId(), Map.of());
                    return new ComparisonItemView(item.getPublicId(), String.valueOf(snapshot.getOrDefault("publicId", "deleted")),
                            String.valueOf(snapshot.getOrDefault("company", "Unknown")), String.valueOf(snapshot.getOrDefault("role", "Unknown")),
                            item.getRankNo(), item.getGuaranteedAnnualCash(), item.getPotentialAnnualCash(), item.getOverallScore(),
                            read(item.getDimensionsJson(), new TypeReference<List<DimensionView>>() {}), item.getExplanationText());
                }).toList();
        return new ComparisonView(entity.getPublicId(), entity.getComparisonVersion(), entity.getName(),
                read(entity.getOfferIdsJson(), new TypeReference<List<String>>() {}),
                read(entity.getWeightsJson(), new TypeReference<Map<String, BigDecimal>>() {}), entity.getCurrencyGroup(),
                Boolean.TRUE.equals(entity.getCashComparable()), entity.getSummaryText(), itemViews,
                entity.getCreatedAt().atOffset(ZoneOffset.UTC));
    }

    private Map<String, Object> snapshot(OfferEntity offer, List<OfferBenefitEntity> benefitValues) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("internalId", offer.getId()); value.put("publicId", offer.getPublicId());
        value.put("company", offer.getCompanyNameSnapshot()); value.put("role", offer.getRoleSnapshot()); value.put("currency", offer.getCurrency());
        value.put("guaranteedAnnualCash", OfferMoney.guaranteedAnnualCash(offer)); value.put("potentialAnnualCash", OfferMoney.potentialAnnualCash(offer));
        value.put("benefits", benefitValues.stream().map(v -> Map.of("name", v.getName(), "type", v.getBenefitType(),
                "value", v.getQuantifiedValue() == null ? "UNKNOWN" : v.getQuantifiedValue(), "currency", v.getCurrency() == null ? "UNKNOWN" : v.getCurrency())).toList());
        return value;
    }

    private List<OfferBenefitEntity> benefitEntities(Long offerId) { return benefits.selectList(new LambdaQueryWrapper<OfferBenefitEntity>().eq(OfferBenefitEntity::getOfferId, offerId)); }
    private BigDecimal benefitTotal(OfferEntity offer) { return benefitEntities(offer.getId()).stream().filter(v -> offer.getCurrency().equals(v.getCurrency())).map(OfferBenefitEntity::getQuantifiedValue).filter(java.util.Objects::nonNull).reduce(null, (a,b) -> a == null ? b : a.add(b)); }
    private static Map<Long, BigDecimal> values(List<OfferEntity> offers, java.util.function.Function<OfferEntity, BigDecimal> mapper) { Map<Long, BigDecimal> result = new LinkedHashMap<>(); offers.forEach(v -> result.put(v.getId(), mapper.apply(v))); return result; }
    private static BigDecimal maximum(Map<Long, BigDecimal> values) { return values.values().stream().filter(java.util.Objects::nonNull).max(BigDecimal::compareTo).orElse(null); }
    private static BigDecimal rating(Map<String, Map<String, BigDecimal>> ratings, String offerId, String key) { return ratings == null || ratings.get(offerId) == null ? null : ratings.get(offerId).get(key); }
    private static void add(List<DimensionView> target, String key, BigDecimal raw, BigDecimal max, Map<String, BigDecimal> weights, String explanation) { addScore(target, key, OfferMoney.score(raw, max), weights.get(key), explanation, raw); }
    private static void addScore(List<DimensionView> target, String key, BigDecimal score, BigDecimal weight, String explanation) { addScore(target, key, score, weight, explanation, score); }
    private static void addScore(List<DimensionView> target, String key, BigDecimal score, BigDecimal weight, String explanation, BigDecimal raw) {
        boolean unknown = score == null; BigDecimal contribution = unknown ? null : score.multiply(weight).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        target.add(new DimensionView(key, raw, score, weight, contribution, unknown, explanation));
    }
    private static Map<String, BigDecimal> orderedWeights(Map<String, BigDecimal> input) { Map<String, BigDecimal> result = new LinkedHashMap<>(); DIMENSIONS.forEach(key -> result.put(key, input.get(key))); return result; }
    private static Map<String, Map<String, BigDecimal>> orderedRatings(Map<String, Map<String, BigDecimal>> input) { if (input == null) return Map.of(); Map<String, Map<String, BigDecimal>> result = new java.util.TreeMap<>(); input.forEach((id, values) -> result.put(id, new java.util.TreeMap<>(values))); return result; }
    private int nextVersion(Long userId) { OfferComparisonEntity last = comparisons.selectOne(new LambdaQueryWrapper<OfferComparisonEntity>().eq(OfferComparisonEntity::getUserId, userId).orderByDesc(OfferComparisonEntity::getComparisonVersion).last("LIMIT 1")); return last == null ? 1 : last.getComparisonVersion() + 1; }
    private OfferComparisonEntity byKey(Long userId, String key) { return comparisons.selectOne(new LambdaQueryWrapper<OfferComparisonEntity>().eq(OfferComparisonEntity::getUserId, userId).eq(OfferComparisonEntity::getIdempotencyKey, key.trim()).last("LIMIT 1")); }
    private ComparisonView sameHash(OfferComparisonEntity value, String hash) { if (!value.getInputHash().equals(hash)) throw new BusinessException(4099007, "Idempotency key was already used with different comparison input", HttpStatus.CONFLICT); return view(value); }
    private static void validateKey(String key) { if (key == null || key.isBlank() || key.length() > 120) throw new ValidationException("Idempotency-Key header is required and must be at most 120 characters"); }
    private static String sha256(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private <T> T read(String value, TypeReference<T> type) { try { return objectMapper.readValue(value, type); } catch (JsonProcessingException exception) { throw new IllegalStateException("Stored comparison JSON is invalid", exception); } }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> castMaps(List<?> values) { return values.stream().map(v -> (Map<String, Object>) v).toList(); }
    private record Ranked(OfferEntity offer, BigDecimal score, List<DimensionView> dimensions, String explanation) { }
}
