package com.linkride.backend.dto.home;

import lombok.Builder;
import lombok.Data;

/**
 * Notification badge summary shown on the Home Screen.
 *
 * <p>Currently stubbed to {@code 0}. Wire to a {@code notifications} table
 * once the Notifications module is introduced.</p>
 */
@Data
@Builder
public class NotificationSummaryDto {
    private int unreadCount;
}
