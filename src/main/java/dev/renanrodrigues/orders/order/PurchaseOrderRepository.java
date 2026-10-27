package dev.renanrodrigues.orders.order;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PurchaseOrderRepository
        extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("select purchaseOrder from PurchaseOrder purchaseOrder where purchaseOrder.id = :orderId")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("orderId") UUID orderId);

    @EntityGraph(attributePaths = "items")
    Optional<PurchaseOrder> findByPartnerIdAndIdempotencyKey(UUID partnerId, String idempotencyKey);
}
