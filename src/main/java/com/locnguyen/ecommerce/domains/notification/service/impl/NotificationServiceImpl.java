package com.locnguyen.ecommerce.domains.notification.service.impl;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.response.PagedResponse;
import com.locnguyen.ecommerce.domains.customer.entity.Customer;
import com.locnguyen.ecommerce.domains.customer.repository.CustomerRepository;
import com.locnguyen.ecommerce.domains.notification.dto.BroadcastNotificationRequest;
import com.locnguyen.ecommerce.domains.notification.dto.NotificationResponse;
import com.locnguyen.ecommerce.domains.notification.dto.UnreadCountResponse;
import com.locnguyen.ecommerce.domains.notification.entity.Notification;
import com.locnguyen.ecommerce.domains.notification.enums.NotificationType;
import com.locnguyen.ecommerce.domains.notification.mapper.NotificationMapper;
import com.locnguyen.ecommerce.domains.notification.repository.NotificationRepository;
import com.locnguyen.ecommerce.domains.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final CustomerRepository customerRepository;

    @Override
    @Transactional
    public void recordReturnUpdate(UUID customerId, UUID returnId, String message) {
        Notification notification = new Notification();
        notification.setCustomer(customerRepository.getReferenceById(customerId));
        notification.setType(NotificationType.RETURN_UPDATED);
        notification.setTitle("Return request update");
        notification.setBody(message);
        notification.setReferenceId(returnId);
        notification.setReferenceType("RETURN");
        notificationRepository.save(notification);
    }

    @Override
    @Async
    @Transactional
    public void send(Customer customer, NotificationType type, String title, String body) {
        send(customer, type, title, body, null, null);
    }

    @Override
    @Async
    @Transactional
    public void send(Customer customer, NotificationType type, String title, String body,
                     UUID referenceId, String referenceType) {
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

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<NotificationResponse> getMyNotifications(Customer customer, Pageable pageable) {
        Page<Notification> page = notificationRepository
                .findByCustomerIdOrderByCreatedAtDesc(customer.getId(), pageable);
        return PagedResponse.of(page.map(notificationMapper::toResponse));
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(UUID notificationId, Customer customer) {
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

    @Override
    @Transactional
    public void markAllAsRead(Customer customer) {
        notificationRepository.markAllReadByCustomerId(customer.getId(), LocalDateTime.now());
    }

    @Override
    @Transactional
    public int broadcast(BroadcastNotificationRequest request) {
        List<Customer> targets = (request.getCustomerIds() == null
                || request.getCustomerIds().isEmpty())
                ? customerRepository.findAllByDeletedFalse()
                : customerRepository.findByIdInAndDeletedFalse(request.getCustomerIds());

        for (Customer customer : targets) {
            Notification entry = new Notification();
            entry.setCustomer(customer);
            entry.setType(request.getType());
            entry.setTitle(request.getTitle());
            entry.setBody(request.getMessage());
            entry.setReferenceId(parseReferenceId(request.getReferenceId()));
            entry.setReferenceType(request.getReferenceType());
            notificationRepository.save(entry);
        }

        log.info("Notification broadcast: type={} count={}", request.getType(), targets.size());
        return targets.size();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Customer customer) {
        long count = notificationRepository.countByCustomerIdAndReadFalse(customer.getId());
        return new UnreadCountResponse(count);
    }

    private UUID parseReferenceId(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
