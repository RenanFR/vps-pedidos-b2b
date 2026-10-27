package dev.renanrodrigues.orders.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateOrderStatusRequest(
        @Schema(example = "APROVADO") @NotNull OrderStatus status,
        @Schema(example = "Pagamento confirmado") @Size(max = 500) String reason) {
}
