package com.locnguyen.ecommerce.common.security;

import com.locnguyen.ecommerce.common.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private static final String SESSION_KEY_PREFIX = "auth:refresh:";
    private static final String PRINCIPAL_INDEX_KEY_PREFIX = "auth:refresh:index:";
    private static final String FAMILY_INDEX_KEY_PREFIX = "auth:refresh:family:";

    private static final String CUSTOMER_FAMILY_INDEX_PREFIX = "auth:refresh:customer-families:";
    private static final String REVOKED_CUSTOMER_FAMILY_PREFIX = "auth:refresh:revoked-customer-family:";
    private static final String REVOKED_LEGACY_ACCESS_PREFIX = "auth:refresh:revoked-legacy-access:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;

    public void storeSession(RefreshSession session, Duration ttl) {
        String sessionKey = sessionKey(session.getPrincipalType(), session.getPrincipalId(), session.getSessionId());
        String principalIndexKey = principalIndexKey(session.getPrincipalType(), session.getPrincipalId());
        String familyIndexKey = familyIndexKey(session.getFamilyId());

        redisTemplate.opsForValue().set(sessionKey, session, ttl);
        redisTemplate.opsForSet().add(principalIndexKey, sessionKey);
        redisTemplate.opsForSet().add(familyIndexKey, sessionKey);
        redisTemplate.expire(principalIndexKey, ttl);
        redisTemplate.expire(familyIndexKey, ttl);
        updateCustomerFamilyIndex(session, true);
    }

    public RefreshSession getSession(AuthPrincipalType principalType, UUID principalId, String sessionId) {
        Object value = redisTemplate.opsForValue().get(sessionKey(principalType, principalId, sessionId));
        return value instanceof RefreshSession session ? session : null;
    }

    public List<RefreshSession> listActiveSessions(AuthPrincipalType principalType, UUID principalId) {
        Set<Object> members = redisTemplate.opsForSet().members(principalIndexKey(principalType, principalId));
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<String> keys = members.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        List<Object> records = redisTemplate.opsForValue().multiGet(keys);
        if (records == null) {
            return List.of();
        }
        Instant now = Instant.now();
        return records.stream()
                .filter(RefreshSession.class::isInstance)
                .map(RefreshSession.class::cast)
                .filter(session -> session.getPrincipalType() == principalType
                        && principalId.equals(session.getPrincipalId())
                        && session.getFamilyId() != null
                        && session.getIssuedAt() != null
                        && session.getRevokedAt() == null
                        && session.getExpiresAt() != null
                        && session.getExpiresAt().isAfter(now))
                .toList();
    }

    public void indexCustomerFamily(RefreshSession session) {
        updateCustomerFamilyIndex(session, false);
    }

    private void updateCustomerFamilyIndex(RefreshSession session, boolean rotation) {
        if (session.getPrincipalType() != AuthPrincipalType.CUSTOMER) {
            return;
        }
        long expiresAt = session.getExpiresAt().toEpochMilli();
        long now = Instant.now().toEpochMilli();
        if (expiresAt <= now) {
            return;
        }
        String key = CUSTOMER_FAMILY_INDEX_PREFIX + session.getPrincipalId();
        if (rotation) {
            redisTemplate.opsForZSet().add(key, session.getFamilyId(), expiresAt);
        } else {
            redisTemplate.opsForZSet().addIfAbsent(key, session.getFamilyId(), expiresAt);
        }
        redisTemplate.expire(key, Duration.ofMillis(appProperties.getJwt().getRefreshTokenExpiration()));
    }

    public List<String> listKnownCustomerFamilies(UUID customerId) {
        String key = CUSTOMER_FAMILY_INDEX_PREFIX + customerId;
        long now = Instant.now().toEpochMilli();
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, now);
        Set<Object> families = redisTemplate.opsForZSet().rangeByScore(key, now + 1, Double.POSITIVE_INFINITY);
        if (families == null) {
            return List.of();
        }
        return families.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    public boolean isCustomerFamilyRevoked(String familyId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_CUSTOMER_FAMILY_PREFIX + familyId));
    }

    public boolean isCustomerAccessRevoked(String username, String familyId) {
        if (familyId != null) {
            return isCustomerFamilyRevoked(familyId);
        }
        return Boolean.TRUE.equals(redisTemplate.hasKey(legacyAccessKey(username)));
    }

    public void revokeCustomerFamily(UUID customerId, String username, String familyId,
                                     Duration familyTtl, Duration legacyAccessTtl) {
        redisTemplate.opsForValue().set(REVOKED_CUSTOMER_FAMILY_PREFIX + familyId, "1", familyTtl);
        // Older access JWTs do not identify a family. Their owners can renew any retained sessions.
        redisTemplate.opsForValue().set(legacyAccessKey(username), "1", legacyAccessTtl);
        revokeFamily(AuthPrincipalType.CUSTOMER, customerId, familyId);
        redisTemplate.opsForZSet().remove(CUSTOMER_FAMILY_INDEX_PREFIX + customerId, familyId);
    }

    private String legacyAccessKey(String username) {
        return REVOKED_LEGACY_ACCESS_PREFIX + hashToken(username);
    }

    public void revokeSession(AuthPrincipalType principalType, UUID principalId, String sessionId) {
        RefreshSession session = getSession(principalType, principalId, sessionId);
        if (session == null) {
            return;
        }
        deleteSession(sessionKey(principalType, principalId, sessionId), session);
    }

    public void revokeFamily(AuthPrincipalType principalType, UUID principalId, String familyId) {
        String familyIndexKey = familyIndexKey(familyId);
        Set<Object> members = redisTemplate.opsForSet().members(familyIndexKey);
        if (members == null || members.isEmpty()) {
            return;
        }

        for (Object member : members) {
            if (!(member instanceof String sessionKey)) {
                continue;
            }
            Object value = redisTemplate.opsForValue().get(sessionKey);
            if (value instanceof RefreshSession session) {
                deleteSession(sessionKey, session);
            } else {
                redisTemplate.delete(sessionKey);
            }
        }

        redisTemplate.delete(familyIndexKey);
        cleanupEmptyPrincipalIndex(principalType, principalId);
    }

    public void revokeAllRefreshSessions(AuthPrincipalType principalType, UUID principalId) {
        String principalIndexKey = principalIndexKey(principalType, principalId);
        Set<Object> members = redisTemplate.opsForSet().members(principalIndexKey);
        if (members == null || members.isEmpty()) {
            return;
        }

        for (Object member : members) {
            if (!(member instanceof String sessionKey)) {
                continue;
            }
            Object value = redisTemplate.opsForValue().get(sessionKey);
            if (value instanceof RefreshSession session) {
                deleteSession(sessionKey, session);
            } else {
                redisTemplate.delete(sessionKey);
            }
        }

        redisTemplate.delete(principalIndexKey);
    }

    public String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private void deleteSession(String sessionKey, RefreshSession session) {
        redisTemplate.delete(sessionKey);
        redisTemplate.opsForSet().remove(
                principalIndexKey(session.getPrincipalType(), session.getPrincipalId()),
                sessionKey
        );
        redisTemplate.opsForSet().remove(familyIndexKey(session.getFamilyId()), sessionKey);
        cleanupEmptyPrincipalIndex(session.getPrincipalType(), session.getPrincipalId());
        cleanupEmptyFamilyIndex(session.getFamilyId());
    }

    private void cleanupEmptyPrincipalIndex(AuthPrincipalType principalType, UUID principalId) {
        String key = principalIndexKey(principalType, principalId);
        Long size = redisTemplate.opsForSet().size(key);
        if (Objects.equals(size, 0L)) {
            redisTemplate.delete(key);
        }
    }

    private void cleanupEmptyFamilyIndex(String familyId) {
        String key = familyIndexKey(familyId);
        Long size = redisTemplate.opsForSet().size(key);
        if (Objects.equals(size, 0L)) {
            redisTemplate.delete(key);
        }
    }

    private String sessionKey(AuthPrincipalType principalType, UUID principalId, String sessionId) {
        return SESSION_KEY_PREFIX + principalType.name() + ":" + principalId + ":" + sessionId;
    }

    private String principalIndexKey(AuthPrincipalType principalType, UUID principalId) {
        return PRINCIPAL_INDEX_KEY_PREFIX + principalType.name() + ":" + principalId;
    }

    private String familyIndexKey(String familyId) {
        return FAMILY_INDEX_KEY_PREFIX + familyId;
    }
}
