package com.locnguyen.ecommerce.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshSessionService {

    private static final String SESSION_KEY_PREFIX = "auth:refresh:";
    private static final String PRINCIPAL_INDEX_KEY_PREFIX = "auth:refresh:index:";
    private static final String FAMILY_INDEX_KEY_PREFIX = "auth:refresh:family:";

    private final RedisTemplate<String, Object> redisTemplate;

    public void storeSession(RefreshSession session, Duration ttl) {
        String sessionKey = sessionKey(session.getPrincipalType(), session.getPrincipalId(), session.getSessionId());
        String principalIndexKey = principalIndexKey(session.getPrincipalType(), session.getPrincipalId());
        String familyIndexKey = familyIndexKey(session.getFamilyId());

        redisTemplate.opsForValue().set(sessionKey, session, ttl);
        redisTemplate.opsForSet().add(principalIndexKey, sessionKey);
        redisTemplate.opsForSet().add(familyIndexKey, sessionKey);
        redisTemplate.expire(principalIndexKey, ttl);
        redisTemplate.expire(familyIndexKey, ttl);
    }

    public RefreshSession getSession(AuthPrincipalType principalType, UUID principalId, String sessionId) {
        Object value = redisTemplate.opsForValue().get(sessionKey(principalType, principalId, sessionId));
        return value instanceof RefreshSession session ? session : null;
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
