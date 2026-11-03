package co.corhuila.drp.payment.app;

import co.corhuila.drp.payment.domain.PaymentState;
import java.time.Instant;

public record ListFilter(
    Principal actor,
    PaymentState state,
    String reservationId,
    Instant createdFrom,
    Instant createdTo,
    int page,
    int limit) {}
