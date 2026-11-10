package co.corhuila.drp.payment.adapters.memory;

import co.corhuila.drp.payment.app.PaymentRepository;
import co.corhuila.drp.payment.app.StoredPayment;
import co.corhuila.drp.payment.domain.PaymentState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class MemoryPayments implements PaymentRepository {
  private final ConcurrentMap<String, StoredPayment> byId = new ConcurrentHashMap<>();

  @Override
  public List<StoredPayment> list() {
    return new ArrayList<>(byId.values());
  }

  @Override
  public Optional<StoredPayment> findById(String id) {
    return Optional.ofNullable(byId.get(id));
  }

  @Override
  public Optional<StoredPayment> findOpenByReservation(String reservationId) {
    return byId.values().stream()
        .filter(row -> row.payment().reservationId().equals(reservationId))
        .filter(
            row ->
                row.payment().state() == PaymentState.PENDING
                    || row.payment().state() == PaymentState.CONFIRMED)
        .findFirst();
  }

  @Override
  public void save(StoredPayment row) {
    byId.put(row.payment().id(), row);
  }

  public void clear() {
    byId.clear();
  }
}
