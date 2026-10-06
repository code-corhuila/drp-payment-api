package co.corhuila.drp.payment.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Payment aggregate. Unique idempotencyKey is BR-004. reservationId is a copy, not a FK.
 */
public final class Payment {
  private final String id;
  private final String reservationId;
  private final Money money;
  private final String idempotencyKey;
  private final PaymentState state;
  private final String providerReference;
  private final Instant createdAt;
  private final Instant updatedAt;

  private Payment(
      String id,
      String reservationId,
      Money money,
      String idempotencyKey,
      PaymentState state,
      String providerReference,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.reservationId = reservationId;
    this.money = money;
    this.idempotencyKey = idempotencyKey;
    this.state = state;
    this.providerReference = providerReference;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static Payment pending(
      String id, String reservationId, Money money, String idempotencyKey, Instant now) {
    requireId(id);
    requireId(reservationId);
    Objects.requireNonNull(money, "money");
    if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
      throw new DomainException(DomainException.Code.INVALID_INPUT, "idempotencyKey 8-128");
    }
    return new Payment(id, reservationId, money, idempotencyKey, PaymentState.PENDING, null, now, now);
  }

  public Payment confirm(String providerReference, Instant now) {
    if (state != PaymentState.PENDING) {
      throw new DomainException(DomainException.Code.INVALID_STATUS_TRANSITION, "confirm from PENDING only");
    }
    return new Payment(id, reservationId, money, idempotencyKey, PaymentState.CONFIRMED, providerReference, createdAt, now);
  }

  public Payment fail(Instant now) {
    if (state != PaymentState.PENDING) {
      throw new DomainException(DomainException.Code.INVALID_STATUS_TRANSITION, "fail from PENDING only");
    }
    return new Payment(id, reservationId, money, idempotencyKey, PaymentState.FAILED, providerReference, createdAt, now);
  }

  public boolean sameIdempotencyKey(String key) {
    return idempotencyKey.equals(key);
  }

  public String id() { return id; }
  public String reservationId() { return reservationId; }
  public Money money() { return money; }
  public int amountCents() { return money.amountCents(); }
  public String currency() { return money.currency(); }
  public String idempotencyKey() { return idempotencyKey; }
  public PaymentState state() { return state; }
  public String providerReference() { return providerReference; }
  public Instant createdAt() { return createdAt; }
  public Instant updatedAt() { return updatedAt; }

  private static void requireId(String v) {
    if (v == null || v.isBlank()) {
      throw new DomainException(DomainException.Code.INVALID_INPUT, "id required");
    }
  }
}
