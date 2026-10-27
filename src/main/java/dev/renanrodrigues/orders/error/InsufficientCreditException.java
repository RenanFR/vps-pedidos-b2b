package dev.renanrodrigues.orders.error;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientCreditException extends RuntimeException {

    private final BigDecimal requiredAmount;
    private final BigDecimal availableCredit;

    public InsufficientCreditException(UUID partnerId, BigDecimal requiredAmount, BigDecimal availableCredit) {
        super("O parceiro " + partnerId + " não tem crédito suficiente: o pedido custa " + requiredAmount
                + " e o crédito disponível é " + availableCredit);
        this.requiredAmount = requiredAmount;
        this.availableCredit = availableCredit;
    }

    public BigDecimal getRequiredAmount() {
        return requiredAmount;
    }

    public BigDecimal getAvailableCredit() {
        return availableCredit;
    }
}
