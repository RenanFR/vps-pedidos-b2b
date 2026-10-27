package dev.renanrodrigues.orders.partner;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "partner")
public class Partner {

    @Id
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal availableCredit;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Partner() {
    }

    public static Partner create(String name, BigDecimal creditLimit, Instant now) {
        Partner partner = new Partner();
        partner.id = UUID.randomUUID();
        partner.name = name;
        partner.creditLimit = creditLimit.setScale(2, RoundingMode.HALF_EVEN);
        partner.availableCredit = partner.creditLimit;
        partner.createdAt = now;
        partner.updatedAt = now;
        return partner;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public BigDecimal getAvailableCredit() {
        return availableCredit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
