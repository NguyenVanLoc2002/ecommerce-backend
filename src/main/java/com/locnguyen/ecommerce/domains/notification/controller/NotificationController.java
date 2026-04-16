package com.locnguyen.ecommerce.domains.notification.controller;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import com.locnguyen.ecommerce.common.response.ApiResponse;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.notification.dto.NotificationResponse;
import com.locnguyen.ecommerce.domains.notification.dto.UnreadCountResponse;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import com.locnguyen.ecommerce.domains.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Notification")
@RestController
@RequestMapping(AppConstants.API_V1 + "/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    @Operation(summary = "Get my notifications")
    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<PagedResponse<NotificationResponse>> getMyNotifications(
            @PageableDefault(size = AppConstants.DEFAULT_PAGE_SIZE) Pageable pageable) {
        return ApiResponse.success(
                notificationService.getMyNotifications(userService.getCurrentCustomer(), pageable));
    }

    @Operation(summary = "Get unread notification count")
    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<UnreadCountResponse> getUnreadCount() {
        return ApiResponse.success(notificationService.getUnreadCount(userService.getCurrentCustomer()));
    }

    @Operation(summary = "Mark a notification as read")
    @PatchMapping("/{id}/read")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ApiResponse.success(
                notificationService.markAsRead(id, userService.getCurrentCustomer()));
    }

    @Operation(summary = "Mark all notifications as read")
    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead(userService.getCurrentCustomer());
        return ApiResponse.noContent();
    }
}
