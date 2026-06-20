package com.linkride.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "transaction_id", updatable = false, nullable = false)
    private UUID transactionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", referencedColumnName = "id", nullable = false)
    private User user;

    @Column(name = "trip_id")
    private String tripId;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "transaction_type")
    private String transactionType; // "CREDIT" or "DEBIT"

    @Column(name = "status")
    private String status; // "PENDING", "SUCCESS", "FAILED"

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}