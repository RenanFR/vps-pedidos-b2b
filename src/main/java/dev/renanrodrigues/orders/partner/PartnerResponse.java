package dev.renanrodrigues.orders.partner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PartnerResponse(
        UUID id,
        String name,
        BigDecimal creditLimit,
        BigDecimal availableCredit,
        Instant createdAt,
        Instant updatedAt) {

    public static PartnerResponse from(Partner partner) {
        return new PartnerResponse(partner.getId(), partner.getName(), partner.getCreditLimit(),
                partner.getAvailableCredit(), partner.getCreatedAt(), partner.getUpdatedAt());
    }
}
