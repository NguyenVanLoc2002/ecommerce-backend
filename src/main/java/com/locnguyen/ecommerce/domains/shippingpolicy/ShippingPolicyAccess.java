package com.locnguyen.ecommerce.domains.shippingpolicy;

import com.locnguyen.ecommerce.common.exception.AppException;
import com.locnguyen.ecommerce.common.exception.ErrorCode;
import com.locnguyen.ecommerce.common.utils.SecurityUtils;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(ShippingPolicyAccess.Settings.class)
public class ShippingPolicyAccess {
    @ConfigurationProperties(prefix = "app.shipping.policy")
    public record Settings(boolean publicationEnabled, Set<UUID> editors, Set<UUID> publishers) {
        public Settings {
            editors = editors == null ? Set.of() : Set.copyOf(editors);
            publishers = publishers == null ? Set.of() : Set.copyOf(publishers);
        }
    }

    private final Settings settings;
    private final UserRepository users;

    ShippingPolicyAccess(Settings settings, UserRepository users) {
        this.settings = settings;
        this.users = users;
    }

    public ShippingPolicyModels.Capabilities capabilities() {
        UUID actor = actor();
        return new ShippingPolicyModels.Capabilities(settings.editors().contains(actor),
                settings.publishers().contains(actor), settings.publicationEnabled());
    }

    UUID requireRead() {
        UUID actor = actor();
        if (settings.editors().contains(actor) || settings.publishers().contains(actor)) {
            return actor;
        }
        throw new AppException(ErrorCode.FORBIDDEN);
    }

    UUID requireEdit() {
        return require(settings.editors());
    }

    UUID requirePublish() {
        return require(settings.publishers());
    }

    boolean publicationEnabled() {
        return settings.publicationEnabled();
    }

    private UUID require(Set<UUID> allowed) {
        UUID actor = actor();
        if (!allowed.contains(actor)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return actor;
    }

    private UUID actor() {
        if (!SecurityUtils.hasAnyRole("ADMIN", "SUPER_ADMIN")) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        return SecurityUtils.getCurrentUsername().flatMap(users::findByEmailAndDeletedFalse)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRoles().stream().anyMatch(role ->
                        "ADMIN".equals(role.getName().name()) || "SUPER_ADMIN".equals(role.getName().name())))
                .map(User::getId)
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
    }
}
