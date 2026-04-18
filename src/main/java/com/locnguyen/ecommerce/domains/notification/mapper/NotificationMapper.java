package com.locnguyen.ecommerce.domains.notification.mapper;

import com.locnguyen.ecommerce.domains.notification.dto.NotificationResponse;
import com.locnguyen.ecommerce.domains.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "type", expression = "java(notification.getType().name())")
    NotificationResponse toResponse(Notification notification);
}
