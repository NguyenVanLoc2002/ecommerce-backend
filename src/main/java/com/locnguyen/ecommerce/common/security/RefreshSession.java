package com.locnguyen.ecommerce.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshSession {

    private String sessionId;
    private String familyId;
    private AuthPrincipalType principalType;
    private UUID principalId;
    private String tokenHash;
    private Instant issuedAt;
    private Instant expiresAt;
    private String userAgent;
    private String ipAddress;
    private Instant revokedAt;
}
