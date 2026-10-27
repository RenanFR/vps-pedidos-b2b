package dev.renanrodrigues.orders.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(CreateOrderRequest request) {
        String canonical = request.partnerId() + "|" + request.items().stream()
                .map(item -> item.productId() + ":" + item.quantity() + ":"
                        + item.unitPrice().stripTrailingZeros().toPlainString())
                .collect(Collectors.joining(","));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException missingAlgorithm) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", missingAlgorithm);
        }
    }
}
