package dev.renanrodrigues.orders.partner;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePartnerRequest(
        @Schema(example = "Distribuidora Aurora") @NotBlank @Size(max = 150) String name,
        @Schema(example = "50000.00") @NotNull @DecimalMin("0.00") @Digits(integer = 17, fraction = 2)
        BigDecimal creditLimit) {
}
