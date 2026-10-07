package com.clinic.schedule.cashdesk.adapter.in.screen;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.clinic.schedule.cashdesk.domain.Means;
import com.clinic.schedule.cashdesk.domain.exception.ChargeRuleViolationException;

/** The cash desk as the staff reads it; the API says it in English (D-13). */
final class CashDeskWords {

    private CashDeskWords() {
    }

    /** An amount as the staff reads it: $ 1.500,50. */
    static String pesos(BigDecimal amount) {
        return new DecimalFormat("$ #,##0.00", DecimalFormatSymbols.getInstance(Locale.forLanguageTag("es-AR")))
                .format(amount);
    }

    static String means(Means means) {
        return switch (means) {
            case CASH -> "Efectivo";
        };
    }

    static String refusal(ChargeRuleViolationException e) {
        return switch (e.reason()) {
            case CHARGE_REQUIRED -> "Falta el importe del cobro.";
            case INVALID_AMOUNT -> "El importe debe ser mayor que cero, con dos decimales como mucho.";
            case AMOUNT_TOO_HIGH -> "El importe no puede superar " + pesos(e.ceiling()) + ".";
            case ALREADY_CHARGED -> "Esa visita ya está cobrada.";
        };
    }
}
