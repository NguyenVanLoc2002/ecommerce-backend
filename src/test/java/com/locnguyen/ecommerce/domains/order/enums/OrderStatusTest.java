package com.locnguyen.ecommerce.domains.order.enums;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link OrderStatus} state machine.
 *
 * Tests every valid and invalid transition defined in the TRANSITIONS map,
 * plus terminal-state semantics.
 */
class OrderStatusTest {

    // ─── Valid transitions ───────────────────────────────────────────────────

    @Nested
    class ValidTransitions {

        @Test
        void pending_can_go_to_awaiting_payment() {
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.AWAITING_PAYMENT)).isTrue();
        }

        @Test
        void pending_can_be_cancelled() {
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        void awaiting_payment_can_go_to_confirmed() {
            assertThat(OrderStatus.AWAITING_PAYMENT.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        }

        @Test
        void awaiting_payment_can_be_cancelled() {
            assertThat(OrderStatus.AWAITING_PAYMENT.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        void confirmed_can_go_to_processing() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.PROCESSING)).isTrue();
        }

        @Test
        void confirmed_can_still_be_cancelled() {
            assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.CANCELLED)).isTrue();
        }

        @Test
        void processing_can_go_to_shipped() {
            assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        }

        @Test
        void shipped_can_go_to_delivered() {
            assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
        }

        @Test
        void delivered_can_go_to_completed() {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.COMPLETED)).isTrue();
        }

        @Test
        void completed_can_go_to_refunded() {
            assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.REFUNDED)).isTrue();
        }
    }

    // ─── Invalid transitions ─────────────────────────────────────────────────

    @Nested
    class InvalidTransitions {

        @Test
        void pending_cannot_go_directly_to_confirmed() {
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
        }

        @Test
        void pending_cannot_skip_to_shipped() {
            assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.SHIPPED)).isFalse();
        }

        @Test
        void processing_cannot_be_cancelled() {
            assertThat(OrderStatus.PROCESSING.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        void shipped_cannot_be_cancelled() {
            assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        void delivered_cannot_be_cancelled() {
            assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }

        @Test
        void completed_cannot_be_cancelled() {
            assertThat(OrderStatus.COMPLETED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
        }
    }

    // ─── Terminal states ─────────────────────────────────────────────────────

    @Nested
    class TerminalStates {

        @Test
        void cancelled_is_terminal() {
            assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        }

        @Test
        void refunded_is_terminal() {
            assertThat(OrderStatus.REFUNDED.isTerminal()).isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class,
                names = {"CANCELLED", "REFUNDED"},
                mode = EnumSource.Mode.EXCLUDE)
        void non_terminal_statuses_are_not_terminal(OrderStatus status) {
            assertThat(status.isTerminal()).isFalse();
        }

        @Test
        void cancelled_cannot_transition_anywhere() {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(OrderStatus.CANCELLED.canTransitionTo(target)).isFalse();
            }
        }

        @Test
        void refunded_cannot_transition_anywhere() {
            for (OrderStatus target : OrderStatus.values()) {
                assertThat(OrderStatus.REFUNDED.canTransitionTo(target)).isFalse();
            }
        }
    }
}
