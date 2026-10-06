package co.corhuila.drp.payment.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentTest {
  private static final Instant NOW = Instant.parse("2026-10-06T17:00:00Z");

  @Test
  void pendingThenConfirm() {
    Payment p = Payment.pending("p1", "r1", Money.cop(5_000_000), "idem-key-01", NOW);
    assertEquals(PaymentState.PENDING, p.state());
    Payment ok = p.confirm("sim-1", NOW.plusSeconds(1));
    assertEquals(PaymentState.CONFIRMED, ok.state());
    assertEquals("sim-1", ok.providerReference());
    assertThrows(DomainException.class, () -> ok.fail(NOW));
  }

  @Test
  void amountMustBePositive() {
    assertThrows(DomainException.class, () -> Money.cop(0));
  }

  @Test
  void idempotencyKeyBounds() {
    assertThrows(
        DomainException.class,
        () -> Payment.pending("p1", "r1", Money.cop(1), "short", NOW));
  }
}
