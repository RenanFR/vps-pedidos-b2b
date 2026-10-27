package dev.renanrodrigues.orders.order;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class OrderSpecifications {

    private OrderSpecifications() {
    }

    static Specification<PurchaseOrder> matching(OrderSearchFilter filter) {
        return Specification.allOf(
                belongsTo(filter.partnerId()),
                hasStatus(filter.status()),
                createdFrom(filter.createdFrom()),
                createdBefore(filter.createdTo()));
    }

    private static Specification<PurchaseOrder> belongsTo(UUID partnerId) {
        return partnerId == null ? null : (root, query, builder) -> builder.equal(root.get("partnerId"), partnerId);
    }

    private static Specification<PurchaseOrder> hasStatus(OrderStatus status) {
        return status == null ? null : (root, query, builder) -> builder.equal(root.get("status"), status);
    }

    private static Specification<PurchaseOrder> createdFrom(OffsetDateTime from) {
        return from == null ? null
                : (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("createdAt"), from.toInstant());
    }

    private static Specification<PurchaseOrder> createdBefore(OffsetDateTime to) {
        return to == null ? null : (root, query, builder) -> builder.lessThan(root.get("createdAt"), to.toInstant());
    }
}
