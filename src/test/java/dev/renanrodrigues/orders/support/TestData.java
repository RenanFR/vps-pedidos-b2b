package dev.renanrodrigues.orders.support;

import dev.renanrodrigues.orders.order.CreateOrderRequest;
import dev.renanrodrigues.orders.order.OrderItemRequest;
import dev.renanrodrigues.orders.partner.Partner;
import dev.renanrodrigues.orders.partner.PartnerRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TestData {

    private TestData() {
    }

    public static Partner partnerWithCredit(PartnerRepository partnerRepository, String creditLimit) {
        return partnerRepository.save(Partner.create("Parceiro de teste", new BigDecimal(creditLimit), Instant.now()));
    }

    public static CreateOrderRequest orderOf(UUID partnerId, int quantity, String unitPrice) {
        return new CreateOrderRequest(partnerId,
                List.of(new OrderItemRequest("SKU-CAFE-500G", quantity, new BigDecimal(unitPrice))));
    }
}
