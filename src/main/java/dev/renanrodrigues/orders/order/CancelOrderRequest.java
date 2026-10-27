package dev.renanrodrigues.orders.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelOrderRequest(@Schema(example = "Cliente desistiu da compra") @Size(max = 500) String reason) {
}
