package co.corhuila.drp.payment.adapters.memory;

import co.corhuila.drp.payment.app.ReservationCatalog;
import co.corhuila.drp.payment.app.ReservationView;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MemoryReservations implements ReservationCatalog {
  private final ConcurrentMap<String, ReservationView> byId = new ConcurrentHashMap<>();

  public void put(ReservationView view) {
    byId.put(view.id(), view);
  }

  public void clear() {
    byId.clear();
  }

  @Override
  public Optional<ReservationView> lookup(String reservationId, String accessToken, String correlationId) {
    return Optional.ofNullable(byId.get(reservationId));
  }
}
