package com.linkride.backend.boarding;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class OtpGenerateResponse {

    private OffsetDateTime expiresAt;
}
