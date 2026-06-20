package com.linkride.backend.dto;

import com.linkride.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private User user;
    private String accessToken;
    private String refreshToken;
    private Integer expiresIn;
}
