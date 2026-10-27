package dev.renanrodrigues.orders.partner;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update Partner partner
               set partner.availableCredit = partner.availableCredit - :amount, partner.updatedAt = :now
             where partner.id = :partnerId and partner.availableCredit >= :amount
            """)
    int debitIfAvailable(@Param("partnerId") UUID partnerId, @Param("amount") BigDecimal amount,
            @Param("now") Instant now);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Partner partner
               set partner.availableCredit = partner.availableCredit + :amount, partner.updatedAt = :now
             where partner.id = :partnerId and partner.availableCredit + :amount <= partner.creditLimit
            """)
    int refund(@Param("partnerId") UUID partnerId, @Param("amount") BigDecimal amount, @Param("now") Instant now);

    @Query("select partner.availableCredit from Partner partner where partner.id = :partnerId")
    BigDecimal findAvailableCredit(@Param("partnerId") UUID partnerId);
}
