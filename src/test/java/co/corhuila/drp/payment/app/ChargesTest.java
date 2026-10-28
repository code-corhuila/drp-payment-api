package co.corhuila.drp.payment.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.corhuila.drp.payment.adapters.memory.MemoryIdempotency;
import co.corhuila.drp.payment.adapters.memory.MemoryOutbox;
import co.corhuila.drp.payment.adapters.memory.MemoryPayments;
import co.corhuila.drp.payment.adapters.memory.MemoryReservations;
import co.corhuila.drp.payment.adapters.provider.ImmediateConfirmProvider;
import co.corhuila.drp.payment.domain.PaymentState;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChargesTest {
  private static final Instant NOW = Instant.parse("2026-10-24T17:00:00Z");
  private static final String MEMBER = "44444444-4444-4444-4444-444444444444";
  private static final String OTHER = "55555555-5555-5555-5555-555555555555";
  private static final String RESERVATION = "22222222-2222-2222-2222-222222222222";
  private static final String BODY =
      "{\"amountCents\":5000000,\"currency\":\"COP\",\"reservationId\":\"" + RESERVATION + "\"}";

  private MemoryReservations reservations;
  private MemoryOutbox outbox;
  private Charges charges;
  private AtomicInteger seq;

  @BeforeEach
  void setUp() {
    reservations = new MemoryReservations();
    reservations.put(new ReservationView(RESERVATION, MEMBER, "PAYMENT_PENDING"));
    outbox = new MemoryOutbox();
    seq = new AtomicInteger();
    charges =
        new Charges(
            new MemoryPayments(),
            new MemoryIdempotency(),
            reservations,
            new ImmediateConfirmProvider(),
            outbox,
            () -> NOW,
            () -> "aaaaaaaa-aaaa-4aaa-8aaa-00000000000" + seq.incrementAndGet());
  }

  @Test
  void createReturnsPendingAndWritesPaymentConfirmed() {
    CreateResult got = charges.create(cmd(MEMBER, "idem-key-01", BODY, "COP"));
    assertEquals(false, got.replay());
    assertEquals(PaymentState.PENDING, got.payment().state());
    assertEquals(1, outbox.unpublished().size());
    assertEquals(OutboxEvent.PAYMENT_CONFIRMED, outbox.unpublished().getFirst().eventType());
    assertTrue(outbox.unpublished().getFirst().payloadJson().contains("\"userId\":\"" + MEMBER + "\""));
    assertEquals(PaymentState.CONFIRMED, charges.get(new Principal(MEMBER, "USER"), got.payment().id()).state());
  }

  @Test
  void replaySameKeySameBodyIs200Shape() {
    CreateResult first = charges.create(cmd(MEMBER, "idem-key-01", BODY, "COP"));
    CreateResult replay = charges.create(cmd(MEMBER, "idem-key-01", BODY, "COP"));
    assertTrue(replay.replay());
    assertEquals(first.payment().id(), replay.payment().id());
    assertEquals(1, outbox.unpublished().size());
  }

  @Test
  void sameKeyDifferentBodyIsConflict() {
    charges.create(cmd(MEMBER, "idem-key-01", BODY, "COP"));
    String other =
        "{\"amountCents\":8000000,\"currency\":\"COP\",\"reservationId\":\"" + RESERVATION + "\"}";
    AppException ex =
        assertThrows(AppException.class, () -> charges.create(cmd(MEMBER, "idem-key-01", other, "COP")));
    assertEquals(AppException.Kind.CONFLICT, ex.kind());
  }

  @Test
  void missingReservationIs404() {
    AppException ex =
        assertThrows(
            AppException.class,
            () ->
                charges.create(
                    cmd(
                        MEMBER,
                        "idem-key-02",
                        "{\"amountCents\":1,\"currency\":\"COP\",\"reservationId\":\"99999999-9999-9999-9999-999999999999\"}",
                        "COP",
                        "99999999-9999-9999-9999-999999999999")));
    assertEquals(AppException.Kind.NOT_FOUND, ex.kind());
  }

  @Test
  void foreignReservationIs404() {
    AppException ex =
        assertThrows(AppException.class, () -> charges.create(cmd(OTHER, "idem-key-03", BODY, "COP")));
    assertEquals(AppException.Kind.NOT_FOUND, ex.kind());
  }

  @Test
  void reservationNotPendingIs422Transition() {
    reservations.put(new ReservationView(RESERVATION, MEMBER, "CONFIRMED"));
    AppException ex =
        assertThrows(AppException.class, () -> charges.create(cmd(MEMBER, "idem-key-04", BODY, "COP")));
    assertEquals(AppException.Kind.TRANSITION, ex.kind());
  }

  @Test
  void currencyMustBeCop() {
    AppException ex =
        assertThrows(AppException.class, () -> charges.create(cmd(MEMBER, "idem-key-05", BODY, "USD")));
    assertEquals(AppException.Kind.BUSINESS, ex.kind());
  }

  @Test
  void secondKeyAgainstOpenPaymentIs422() {
    charges.create(cmd(MEMBER, "idem-key-01", BODY, "COP"));
    AppException ex =
        assertThrows(AppException.class, () -> charges.create(cmd(MEMBER, "idem-key-99", BODY, "COP")));
    assertEquals(AppException.Kind.BUSINESS, ex.kind());
    assertEquals("La reserva ya tiene un pago en curso o confirmado.", ex.getMessage());
  }

  @Test
  void userDoesNotSeeForeignPayment() {
    CreateResult got = charges.create(cmd(MEMBER, "idem-key-01", BODY, "COP"));
    AppException ex =
        assertThrows(
            AppException.class, () -> charges.get(new Principal(OTHER, "USER"), got.payment().id()));
    assertEquals(AppException.Kind.NOT_FOUND, ex.kind());
  }

  @Test
  void listEnvelopeOrdersNewestFirst() {
    charges.create(cmd(MEMBER, "idem-key-01", BODY, "COP"));
    PaymentPage page =
        charges.list(new ListFilter(new Principal(MEMBER, "USER"), null, null, null, null, 1, 20));
    assertEquals(1, page.total());
    assertEquals(1, page.totalPages());
    assertEquals(PaymentState.CONFIRMED, page.items().getFirst().state());
  }

  private static CreateCommand cmd(String sub, String key, String body, String currency) {
    return cmd(sub, key, body, currency, RESERVATION);
  }

  private static CreateCommand cmd(String sub, String key, String body, String currency, String reservationId) {
    return new CreateCommand(
        new Principal(sub, "USER"), reservationId, 5_000_000, currency, key, body, "token", "trace-1");
  }
}
