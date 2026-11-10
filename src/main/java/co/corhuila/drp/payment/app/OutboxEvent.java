package co.corhuila.drp.payment.app;

import java.time.Instant;

/** Fact stored with the aggregate write. Publisher + payment.outbox come with Postgres. */
public record OutboxEvent(
    String eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    String topic,
    Instant occurredAt,
    int version,
    String payloadJson,
    String correlationId,
    boolean published) {
  public static final String PAYMENT_CONFIRMED = "PaymentConfirmed";
  public static final String PAYMENT_FAILED = "PaymentFailed";
  public static final String TOPIC_CONFIRMED = "payment.confirmed";
  public static final String TOPIC_FAILED = "payment.failed";

  public OutboxEvent publishedCopy() {
    return new OutboxEvent(
        eventId,
        eventType,
        aggregateId,
        aggregateType,
        topic,
        occurredAt,
        version,
        payloadJson,
        correlationId,
        true);
  }
}
