package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.common.constants.AppConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed JWT access token blacklist.
 *
 * <p>When a user logs out, the access token is stored here until its natural expiry.
 * The {@link JwtAuthenticationFilter} checks this list before accepting any token.
 *
 * <p>TTL is set to the token's remaining lifetime so Redis self-purges expired entries —
 * no manual cleanup job needed.
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider tokenProvider;

    /**
     * Add a token to the blacklist. No-op if the token is already expired.
     */
    public void blacklist(String token) {
        Date expiry = tokenProvider.extractExpiration(token);
        long ttlMillis = expiry.getTime() - System.currentTimeMillis();
        if (ttlMillis > 0) {
            redisTemplate.opsForValue().set(
                    AppConstants.REDIS_BLACKLIST_TOKEN_PREFIX + token,
                    Boolean.TRUE,
                    ttlMillis,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    /**
     * Returns {@code true} if the token has been blacklisted (user logged out).
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(AppConstants.REDIS_BLACKLIST_TOKEN_PREFIX + token)
        );
    }
}
