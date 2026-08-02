package com.linkride.backend.notification;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UnreadCountResponse {
    private long count;
}
