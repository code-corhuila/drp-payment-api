package co.corhuila.drp.payment.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FanoutPublisherTest {
  @Test
  void reservationFailureStopsFanout() {
    List<String> seen = new ArrayList<>();
    FanoutPublisher fan =
        new FanoutPublisher(
            ev -> {
              throw new IllegalStateException("reservation down");
            },
            ev -> seen.add(ev.eventId()));
    assertThrows(IllegalStateException.class, () -> fan.publish(event("e-1")));
    assertTrue(seen.isEmpty());
  }

  @Test
  void notificationFailureDoesNotFailRequiredHop() {
    List<String> reserved = new ArrayList<>();
    FanoutPublisher fan =
        new FanoutPublisher(
            ev -> reserved.add(ev.eventId()),
            ev -> {
              throw new IllegalStateException("notification down");
            });
    fan.publish(event("e-2"));
    assertEquals(List.of("e-2"), reserved);
  }

  private static OutboxEvent event(String id) {
    return new OutboxEvent(
        id,
        OutboxEvent.PAYMENT_CONFIRMED,
        "p1",
        "Payment",
        OutboxEvent.TOPIC_CONFIRMED,
        Instant.parse("2026-10-24T18:00:00Z"),
        1,
        "{\"paymentId\":\"p1\",\"reservationId\":\"r1\",\"amountCents\":1,\"currency\":\"COP\",\"userId\":\"u1\"}",
        "trace",
        false);
  }
}
