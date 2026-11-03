package co.corhuila.drp.payment.app;

import java.util.Optional;

public interface ReservationCatalog {
  Optional<ReservationView> lookup(String reservationId, String accessToken, String correlationId);
}
