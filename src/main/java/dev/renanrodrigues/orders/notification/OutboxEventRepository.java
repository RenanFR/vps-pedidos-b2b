package dev.renanrodrigues.orders.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            select * from outbox_event
             where published_at is null and attempts < :maxAttempts
             order by created_at
             limit :batchSize
             for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockNextPending(@Param("maxAttempts") int maxAttempts, @Param("batchSize") int batchSize);

    List<OutboxEvent> findByAggregateIdOrderByCreatedAt(UUID aggregateId);
}
