package co.corhuila.drp.payment.app;

import java.util.List;

public interface Outbox {
  void append(OutboxEvent event);

  List<OutboxEvent> unpublished();
}
