package com.clinic.schedule.cashdesk;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.clinic.schedule.cashdesk.domain.AmountCeiling;

/** Wiring for the module: what may change comes from configuration, so the domain keeps no Spring (D-16). */
@Configuration
class CashDeskConfiguration {

    /** The most a single charge may be (D-87). */
    @Bean
    AmountCeiling amountCeiling(@Value("${app.cash-desk.max-amount}") BigDecimal maxAmount) {
        return new AmountCeiling(maxAmount);
    }
}
