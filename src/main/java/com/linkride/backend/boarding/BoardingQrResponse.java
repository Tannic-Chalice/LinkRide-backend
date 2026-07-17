package com.linkride.backend.boarding;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
@Builder
public class BoardingQrResponse {

    private String token;
    private OffsetDateTime expiresAt;

    public static BoardingQrResponse from(BoardingToken token) {
        return BoardingQrResponse.builder()
                .token(token.getToken())
                .expiresAt(token.getExpiresAt())
                .build();
    }
}
