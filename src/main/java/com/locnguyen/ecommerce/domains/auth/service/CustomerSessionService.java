package com.locnguyen.ecommerce.domains.auth.service;

import com.locnguyen.ecommerce.domains.auth.dto.CustomerSessionResponse;

import java.util.List;
import java.util.UUID;

public interface CustomerSessionService {

    List<CustomerSessionResponse> listCurrentCustomerSessions(String refreshTokenFromCookie);

    void revokeSession(UUID sessionId, String refreshTokenFromCookie);

    void revokeOtherSessions(String refreshTokenFromCookie);
}
