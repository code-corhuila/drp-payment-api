package co.corhuila.drp.payment.app;

public interface EventPublisher {
  void publish(OutboxEvent event);

  EventPublisher NOOP = event -> {};
}
