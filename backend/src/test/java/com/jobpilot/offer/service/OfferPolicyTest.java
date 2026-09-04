package com.jobpilot.offer.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jobpilot.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class OfferPolicyTest {
    @Test void permitsReceivedToConsidering(){assertDoesNotThrow(()->OfferPolicy.transition("RECEIVED","CONSIDERING"));}
    @Test void permitsConsideringToAccepted(){assertDoesNotThrow(()->OfferPolicy.transition("CONSIDERING","ACCEPTED"));}
    @Test void rejectsAcceptedToConsidering(){assertThrows(BusinessException.class,()->OfferPolicy.transition("ACCEPTED","CONSIDERING"));}
    @Test void identifiesTerminalStatuses(){assertTrue(OfferPolicy.terminal("DECLINED"));assertFalse(OfferPolicy.terminal("RECEIVED"));}
}
