package co.corhuila.drp.payment.adapters.memory;

import co.corhuila.drp.payment.app.Outbox;
import co.corhuila.drp.payment.app.OutboxEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MemoryOutbox implements Outbox {
  private final CopyOnWriteArrayList<OutboxEvent> events = new CopyOnWriteArrayList<>();

  @Override
  public void append(OutboxEvent event) {
    events.add(event);
  }

  public void clear() {
    events.clear();
  }

  @Override
  public List<OutboxEvent> unpublished() {
    List<OutboxEvent> out = new ArrayList<>();
    for (OutboxEvent event : events) {
      if (!event.published()) {
        out.add(event);
      }
    }
    return out;
  }

  @Override
  public void markPublished(String eventId) {
    for (int i = 0; i < events.size(); i++) {
      OutboxEvent event = events.get(i);
      if (event.eventId().equals(eventId)) {
        events.set(i, event.publishedCopy());
      }
    }
  }
}
