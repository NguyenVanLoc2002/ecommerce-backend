package com.locnguyen.ecommerce.domains.notification.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {

    private Long id;
    private String type;
    private String title;
    private String body;
    private Long referenceId;
    private String referenceType;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
