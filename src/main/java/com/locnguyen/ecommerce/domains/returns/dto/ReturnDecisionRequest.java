package com.locnguyen.ecommerce.domains.returns.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ReturnDecisionRequest(@NotBlank @Size(max = 1000) String reason,
                                    LocalDateTime deliveredAt) {}
