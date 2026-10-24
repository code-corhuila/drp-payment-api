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
}
