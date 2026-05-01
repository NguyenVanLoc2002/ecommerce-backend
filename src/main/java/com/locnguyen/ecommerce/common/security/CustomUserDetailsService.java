package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.domains.user.entity.Role;
import com.locnguyen.ecommerce.domains.user.entity.User;
import com.locnguyen.ecommerce.domains.user.enums.UserStatus;
import com.locnguyen.ecommerce.domains.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads user data for Spring Security's {@link org.springframework.security.authentication.AuthenticationManager}.
 *
 * <p>Used exclusively by the <b>login</b> flow — the JWT filter does NOT call this
 * class (it builds {@code Authentication} directly from token claims).
 *
 * <p>Maps application {@link User} entity to Spring Security's {@link UserDetails}:
 * <ul>
 *   <li>{@code username} ← {@code user.email}</li>
 *   <li>{@code password} ← {@code user.passwordHash}</li>
 *   <li>{@code authorities} ← {@code ROLE_ + role.name} for each assigned role</li>
 *   <li>{@code disabled} ← {@code status != ACTIVE}</li>
 *   <li>{@code locked} ← {@code status == LOCKED}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmailAndDeletedFalse(email)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "No account found with email: " + email));

        List<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .toList();

        boolean isActive = user.getStatus() == UserStatus.ACTIVE;
        boolean isLocked = user.getStatus() == UserStatus.LOCKED;

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!isActive)
                .accountLocked(isLocked)
                .accountExpired(false)
                .credentialsExpired(false)
                .build();
    }
}
