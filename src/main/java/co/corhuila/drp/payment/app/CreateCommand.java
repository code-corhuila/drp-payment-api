package co.corhuila.drp.payment.app;

public record CreateCommand(
    Principal actor,
    String reservationId,
    int amountCents,
    String currency,
    String idempotencyKey,
    String normalizedBody,
    String accessToken,
    String correlationId) {}
