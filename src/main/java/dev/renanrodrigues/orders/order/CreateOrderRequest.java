package dev.renanrodrigues.orders.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID partnerId,
        @NotEmpty @Size(max = 500) List<@Valid @NotNull OrderItemRequest> items) {
}
