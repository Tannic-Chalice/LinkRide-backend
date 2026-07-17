package com.linkride.backend.boarding;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CheckInRequest {

    @NotBlank
    private String token;
}
