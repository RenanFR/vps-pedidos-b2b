package dev.renanrodrigues.orders.order;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OrderSearchFilter(UUID partnerId, OrderStatus status, OffsetDateTime createdFrom,
        OffsetDateTime createdTo) {
}
