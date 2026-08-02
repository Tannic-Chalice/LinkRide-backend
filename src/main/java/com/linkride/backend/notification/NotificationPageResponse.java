package com.linkride.backend.notification;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/** A plain, stable pagination envelope — deliberately not returning Spring Data's {@code Page}
 * directly, keeping this API's on-the-wire shape independent of Spring Data internals. */
@Data
@Builder
public class NotificationPageResponse {

    private List<NotificationResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static NotificationPageResponse from(Page<Notification> pageResult) {
        return NotificationPageResponse.builder()
                .content(pageResult.getContent().stream().map(NotificationResponse::from).toList())
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .build();
    }
}
