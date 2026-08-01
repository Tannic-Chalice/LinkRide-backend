package com.linkride.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Validated input for the dev-only {@code POST /api/users/test} endpoint (Phase 5 §4 —
 * see backend/docs/phase-5-platform-hardening.md). Replaces accepting the raw {@code User}
 * JPA entity as a request body directly.
 *
 * <p>{@code id} is required (not server-generated) because this endpoint exists to create a
 * local profile row for a Supabase-issued UUID without going through real Supabase signup.</p>
 */
@Data
public class TestUserRequest {

    @NotNull(message = "id is required")
    private UUID id;

    @NotBlank(message = "fullName is required")
    private String fullName;

    @NotBlank(message = "collegeEmail is required")
    @Email(message = "collegeEmail must be a valid email address")
    private String collegeEmail;

    @NotBlank(message = "phoneNumber is required")
    private String phoneNumber;

    private String gender;
}
