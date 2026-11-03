package co.corhuila.drp.payment.app;

/** Drains unpublished rows. AMQP replaces the HTTP sink later. */
public final class OutboxRelay {
  private final Outbox outbox;
  private final EventPublisher publisher;

  public OutboxRelay(Outbox outbox, EventPublisher publisher) {
    this.outbox = outbox;
    this.publisher = publisher == null ? EventPublisher.NOOP : publisher;
  }

  public void drain() {
    for (OutboxEvent event : outbox.unpublished()) {
      publisher.publish(event);
      outbox.markPublished(event.eventId());
    }
  }
}
