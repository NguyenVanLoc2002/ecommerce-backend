package com.locnguyen.ecommerce.domains.notification.dto;

import com.locnguyen.ecommerce.domains.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Schema(description = "Admin request to send a notification to one or more customers")
public class BroadcastNotificationRequest {

    @NotNull
    private NotificationType type;

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotBlank
    @Size(max = 5000)
    private String message;

    @Size(max = 50)
    private String referenceType;

    @Size(max = 100)
    private String referenceId;

    @Schema(description = "Target customer IDs. Leave empty to broadcast to ALL customers.")
    private List<Long> customerIds;
}
