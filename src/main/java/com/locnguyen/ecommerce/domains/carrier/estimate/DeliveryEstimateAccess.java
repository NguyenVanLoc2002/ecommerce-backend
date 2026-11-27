package com.locnguyen.ecommerce.domains.carrier.estimate;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.RoleName;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(DeliveryEstimateAccess.Settings.class)
public class DeliveryEstimateAccess {
    @ConfigurationProperties(prefix = "app.shipping.delivery-rules")
    public record Settings(Set<UUID> managers) {
        public Settings {
            managers = managers == null ? Set.of() : Set.copyOf(managers);
        }
    }

    private final UserRepository users;
    private final Settings settings;

    UUID requireManager() {
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return SecurityUtils.getCurrentUsername().flatMap(users::findByEmailAndDeletedFalse)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRoles().stream().anyMatch(role ->
                        role.getName() == RoleName.ADMIN || role.getName() == RoleName.SUPER_ADMIN))
                .map(User::getId)
                .filter(settings.managers()::contains)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
    }
}
