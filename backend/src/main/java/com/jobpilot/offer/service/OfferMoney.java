package com.jobpilot.offer.service;

import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.offer.domain.OfferEntity;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class OfferMoney {
    private static final BigDecimal TWELVE = new BigDecimal("12");
    private OfferMoney() { }

    public static void validate(OfferEntity offer) {
        if (offer.getBaseSalary() == null || offer.getBaseSalary().signum() < 0) throw new ValidationException("baseSalary must be non-negative");
        if (offer.getSalaryMonths() == null || offer.getSalaryMonths().signum() <= 0 || offer.getSalaryMonths().compareTo(new BigDecimal("36")) > 0) {
            throw new ValidationException("salaryMonths must be within 0.01..36");
        }
        if (offer.getVariableBonusMin() != null && offer.getVariableBonusMax() != null
                && offer.getVariableBonusMax().compareTo(offer.getVariableBonusMin()) < 0) {
            throw new ValidationException("variableBonusMax must be greater than or equal to variableBonusMin");
        }
    }

    public static BigDecimal guaranteedAnnualCash(OfferEntity offer) {
        BigDecimal base = "MONTHLY".equals(offer.getSalaryPeriod())
                ? offer.getBaseSalary().multiply(offer.getSalaryMonths()) : offer.getBaseSalary();
        return money(base.add(zero(offer.getGuaranteedBonus())));
    }

    public static BigDecimal potentialAnnualCash(OfferEntity offer) {
        if (offer.getVariableBonusMax() == null) return null;
        return money(guaranteedAnnualCash(offer).add(offer.getVariableBonusMax()));
    }

    public static BigDecimal probationMonthlyCash(OfferEntity offer) {
        if (offer.getProbationSalaryRatio() == null) return null;
        BigDecimal monthly = "MONTHLY".equals(offer.getSalaryPeriod())
                ? offer.getBaseSalary() : offer.getBaseSalary().divide(TWELVE, 8, RoundingMode.HALF_UP);
        return money(monthly.multiply(offer.getProbationSalaryRatio()));
    }

    public static BigDecimal score(BigDecimal value, BigDecimal maximum) {
        if (value == null || maximum == null || maximum.signum() == 0) return null;
        return value.multiply(new BigDecimal("100")).divide(maximum, 2, RoundingMode.HALF_UP).min(new BigDecimal("100"));
    }

    public static BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
