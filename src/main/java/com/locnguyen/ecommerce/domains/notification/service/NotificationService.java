package com.locnguyen.ecommerce.domains.notification.service;

import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.notification.dto.BroadcastNotificationRequest;
import com.locnguyen.ecommerce.domains.notification.dto.NotificationResponse;
import com.locnguyen.ecommerce.domains.notification.dto.UnreadCountResponse;
import com.locnguyen.ecommerce.domains.notification.enums.NotificationType;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface NotificationService {

    void recordReturnUpdate(UUID customerId, UUID returnId, String message);

    void send(Customer customer, NotificationType type, String title, String body);

    void send(Customer customer, NotificationType type, String title, String body,
              UUID referenceId, String referenceType);

    PagedResponse<NotificationResponse> getMyNotifications(Customer customer, Pageable pageable);

    NotificationResponse markAsRead(UUID notificationId, Customer customer);

    void markAllAsRead(Customer customer);

    int broadcast(BroadcastNotificationRequest request);

    UnreadCountResponse getUnreadCount(Customer customer);
}
