package co.corhuila.drp.payment.app;

/**
 * Reservation consume is required (E-23 confirm). Notification is best-effort (BR-006): a failed
 * inbox write must not undo the payment or the reservation hop.
 */
public final class FanoutPublisher implements EventPublisher {
  private final EventPublisher reservation;
  private final EventPublisher notification;

  public FanoutPublisher(EventPublisher reservation, EventPublisher notification) {
    this.reservation = reservation;
    this.notification = notification == null ? EventPublisher.NOOP : notification;
  }

  @Override
  public void publish(OutboxEvent event) {
    reservation.publish(event);
    try {
      notification.publish(event);
    } catch (RuntimeException ignored) {
      // BR-006
    }
  }
}
