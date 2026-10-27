package dev.renanrodrigues.orders.order;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record OrderItemRequest(
        @Schema(example = "SKU-CAFE-500G") @NotBlank @Size(max = 100) String productId,
        @Schema(example = "10") @Positive @Max(100_000) int quantity,
        @Schema(example = "24.90") @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2)
        BigDecimal unitPrice) {
}
