package com.linkride.backend.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
public class User {
    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    @Column(name = "full_name", nullable = false)
    private String fullName;
    @Column(name = "college_email", nullable = false, unique = true)
    private String collegeEmail;
    @Column(name = "phone_number", nullable = false, unique = true)
    private String phoneNumber;
    @Column(name = "gender")
    private String gender;
    @Column(name = "rating")
    private BigDecimal rating = new BigDecimal("5.0");
    @Column(name = "is_verified")
    private Boolean isVerified = false;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}