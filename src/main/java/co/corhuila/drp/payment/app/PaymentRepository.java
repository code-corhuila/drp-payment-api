package co.corhuila.drp.payment.app;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
  List<StoredPayment> list();

  Optional<StoredPayment> findById(String id);

  Optional<StoredPayment> findOpenByReservation(String reservationId);

  void save(StoredPayment row);
}
