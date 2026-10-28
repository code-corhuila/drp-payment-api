package co.corhuila.drp.payment.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import co.corhuila.drp.payment.adapters.memory.MemoryOutbox;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class OutboxRelayTest {
  @Test
  void drainMarksPublishedWhenSinkAccepts() {
    MemoryOutbox box = new MemoryOutbox();
    box.append(event("e-1"));
    List<String> sent = new ArrayList<>();
    new OutboxRelay(box, ev -> sent.add(ev.eventId())).drain();
    assertEquals(List.of("e-1"), sent);
    assertTrue(box.unpublished().isEmpty());
  }

  @Test
  void drainKeepsRowWhenSinkFails() {
    MemoryOutbox box = new MemoryOutbox();
    box.append(event("e-2"));
    OutboxRelay relay =
        new OutboxRelay(
            box,
            ev -> {
              throw new IllegalStateException("down");
            });
    assertThrows(IllegalStateException.class, relay::drain);
    assertEquals(1, box.unpublished().size());
  }

  private static OutboxEvent event(String id) {
    return new OutboxEvent(
        id,
        OutboxEvent.PAYMENT_CONFIRMED,
        "p1",
        "Payment",
        OutboxEvent.TOPIC_CONFIRMED,
        Instant.parse("2026-10-24T17:00:00Z"),
        1,
        "{\"paymentId\":\"p1\",\"reservationId\":\"r1\",\"amountCents\":1,\"currency\":\"COP\"}",
        "trace-1",
        false);
  }
}
