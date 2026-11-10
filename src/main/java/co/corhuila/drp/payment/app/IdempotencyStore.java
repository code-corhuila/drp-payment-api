package co.corhuila.drp.payment.app;

import java.util.Optional;

public interface IdempotencyStore {
  Optional<Replay> get(String subject, String route, String key);

  void put(String subject, String route, String key, Replay replay);

  record Replay(String normalizedBody, StoredPayment stored) {}
}
