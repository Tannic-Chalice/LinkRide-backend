package com.linkride.backend.notification;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationPreferenceDto {

    @NotNull
    private NotificationCategory category;

    @NotNull
    private Boolean pushEnabled;

    public static NotificationPreferenceDto from(NotificationPreference preference) {
        return NotificationPreferenceDto.builder()
                .category(preference.getCategory())
                .pushEnabled(preference.getPushEnabled())
                .build();
    }

    /** The default that applies when no {@link NotificationPreference} row exists for a category. */
    public static NotificationPreferenceDto defaultFor(NotificationCategory category) {
        return NotificationPreferenceDto.builder()
                .category(category)
                .pushEnabled(true)
                .build();
    }
}
