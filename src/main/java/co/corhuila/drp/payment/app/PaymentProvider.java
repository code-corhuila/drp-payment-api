package co.corhuila.drp.payment.app;

import co.corhuila.drp.payment.domain.Payment;

public interface PaymentProvider {
  Outcome charge(Payment pending);

  enum Outcome {
    CONFIRMED,
    FAILED
  }
}
