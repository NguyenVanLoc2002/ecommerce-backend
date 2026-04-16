package com.locnguyen.ecommerce.domains.notification.service;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.notification.dto.NotificationResponse;
import com.locnguyen.ecommerce.domains.notification.dto.UnreadCountResponse;
import com.locnguyen.ecommerce.domains.notification.entity.Notification;
import com.locnguyen.ecommerce.domains.notification.enums.NotificationType;
import com.locnguyen.ecommerce.domains.notification.mapper.NotificationMapper;
import com.locnguyen.ecommerce.domains.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;

    // ─── Internal send API ───────────────────────────────────────────────────

    /**
     * Persist an in-app notification asynchronously.
     * Failures are swallowed so they never disrupt the calling business flow.
     */
    @Async
    @Transactional
    public void send(Customer customer, NotificationType type, String title, String body) {
        send(customer, type, title, body, null, null);
    }

    /**
     * Persist an in-app notification with an optional deep-link reference.
     *
     * @param referenceId   PK of the linked entity (e.g. orderId)
     * @param referenceType domain label for the linked entity (e.g. "ORDER")
     */
    @Async
    @Transactional
    public void send(Customer customer, NotificationType type, String title, String body,
                     Long referenceId, String referenceType) {
        try {
            Notification notification = new Notification();
            notification.setCustomer(customer);
            notification.setType(type);
            notification.setTitle(title);
            notification.setBody(body);
            notification.setReferenceId(referenceId);
            notification.setReferenceType(referenceType);
            notificationRepository.save(notification);

            log.debug("Notification sent: type={} customerId={}", type, customer.getId());
        } catch (Exception ex) {
            log.error("Failed to send notification: type={} customerId={} — {}",
                    type, customer.getId(), ex.getMessage());
        }
    }

    // ─── Customer-facing operations ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getMyNotifications(Customer customer, Pageable pageable) {
        Page<Notification> page = notificationRepository
                .findByCustomerIdOrderByCreatedAtDesc(customer.getId(), pageable);
        return PagedResponse.of(page.map(notificationMapper::toResponse));
    }

    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Customer customer) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getCustomer().getId().equals(customer.getId())) {
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return notificationMapper.toResponse(notification);
    }

    @Transactional
    public void markAllAsRead(Customer customer) {
        notificationRepository.markAllReadByCustomerId(customer.getId(), LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Customer customer) {
        long count = notificationRepository.countByCustomerIdAndReadFalse(customer.getId());
        return new UnreadCountResponse(count);
    }
}
