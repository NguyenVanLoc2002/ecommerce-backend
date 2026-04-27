package com.locnguyen.ecommerce.domains.notification.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.domains.notification.dto.BroadcastNotificationRequest;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin — Notifications", description = "Notification broadcast (admin only)")
@RestController
@RequiredArgsConstructor
@RequestMapping(AppConstants.API_V1 + "/admin/notifications")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(
            summary = "Broadcast a notification",
            description = "Send to specific customerIds or leave empty to reach all customers."
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/broadcast")
    public ApiResponse<Map<String, Integer>> broadcast(
            @Valid @RequestBody BroadcastNotificationRequest request) {
        int sent = notificationService.broadcast(request);
        return ApiResponse.created(Map.of("sent", sent));
    }
}
