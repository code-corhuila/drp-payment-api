package co.corhuila.drp.payment.adapters.memory;

import co.corhuila.drp.payment.app.IdempotencyStore;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MemoryIdempotency implements IdempotencyStore {
  private final ConcurrentMap<String, Replay> rows = new ConcurrentHashMap<>();

  @Override
  public Optional<Replay> get(String subject, String route, String key) {
    return Optional.ofNullable(rows.get(slot(subject, route, key)));
  }

  @Override
  public void put(String subject, String route, String key, Replay replay) {
    rows.put(slot(subject, route, key), replay);
  }

  public void clear() {
    rows.clear();
  }

  private static String slot(String subject, String route, String key) {
    return subject + "\0" + route + "\0" + key;
  }
}
