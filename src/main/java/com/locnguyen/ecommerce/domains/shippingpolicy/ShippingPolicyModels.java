package com.locnguyen.ecommerce.domains.shippingpolicy;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ShippingPolicyModels {
    private ShippingPolicyModels() {}

    public enum Status { DRAFT, PUBLISHED, ARCHIVED }

    public record Draft(@NotBlank @Size(max = 200) String title,
                        @NotBlank @Size(max = 20_000) String content,
                        @NotNull Instant effectiveFrom) {}

    public record Revision(@Min(1) int revision) {}

    public record Edit(@Min(1) int revision, @NotNull @jakarta.validation.Valid Draft draft) {}

    public record Summary(long version, int revision, String title, Status status,
                          Instant effectiveFrom, Instant publishedAt) {}

    public record Policy(long version, int revision, String title, String content, Status status,
                         Instant effectiveFrom, Instant publishedAt, UUID publishedBy) {}

    public record PublicPolicy(long version, String title, String content, Instant effectiveFrom,
                               Instant publishedAt) {}

    public record Change(int revision, String title, String content, Status status,
                         Instant effectiveFrom, UUID actor, Instant changedAt) {}

    public record HistoryPage(List<Change> items, boolean hasNext) {}

    public record Page(List<Summary> items, boolean hasNext) {}

    public record Capabilities(boolean canEdit, boolean canPublish, boolean publicationEnabled) {}
}
