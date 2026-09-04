package com.jobpilot.offer.service;

import static org.junit.jupiter.api.Assertions.*;

import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.offer.domain.OfferEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OfferMoneyTest {
    @Test void calculatesMonthlyGuaranteedAnnualCash() { OfferEntity o=offer("10000","MONTHLY","13");o.setGuaranteedBonus(new BigDecimal("20000"));assertEquals(new BigDecimal("150000.00"),OfferMoney.guaranteedAnnualCash(o)); }
    @Test void calculatesAnnualGuaranteedCash() { OfferEntity o=offer("180000","ANNUAL","12");o.setGuaranteedBonus(new BigDecimal("10000"));assertEquals(new BigDecimal("190000.00"),OfferMoney.guaranteedAnnualCash(o)); }
    @Test void potentialCashIsUnknownWithoutMaximumBonus() { assertNull(OfferMoney.potentialAnnualCash(offer("10000","MONTHLY","12"))); }
    @Test void calculatesPotentialCash() { OfferEntity o=offer("10000","MONTHLY","12");o.setVariableBonusMax(new BigDecimal("30000"));assertEquals(new BigDecimal("150000.00"),OfferMoney.potentialAnnualCash(o)); }
    @Test void calculatesProbationMonthlyCash() { OfferEntity o=offer("12000","MONTHLY","12");o.setProbationSalaryRatio(new BigDecimal("0.8"));assertEquals(new BigDecimal("9600.00"),OfferMoney.probationMonthlyCash(o)); }
    @Test void normalizesScoresDeterministically() { assertEquals(new BigDecimal("50.00"),OfferMoney.score(new BigDecimal("10"),new BigDecimal("20"))); }
    @Test void returnsUnknownScoreForMissingValue() { assertNull(OfferMoney.score(null,new BigDecimal("20"))); }
    @Test void rejectsDescendingVariableBonusRange() { OfferEntity o=offer("1","MONTHLY","12");o.setVariableBonusMin(new BigDecimal("5"));o.setVariableBonusMax(new BigDecimal("4"));assertThrows(ValidationException.class,()->OfferMoney.validate(o)); }
    private static OfferEntity offer(String base,String period,String months){OfferEntity o=new OfferEntity();o.setBaseSalary(new BigDecimal(base));o.setSalaryPeriod(period);o.setSalaryMonths(new BigDecimal(months));return o;}
}
