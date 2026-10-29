package co.corhuila.drp.payment.adapters.provider;

import co.corhuila.drp.payment.app.PaymentProvider;
import co.corhuila.drp.payment.domain.Payment;

/** MVP adapter (DP-02). Swap this class when a real provider exists; E-16 stays. */
public final class ImmediateConfirmProvider implements PaymentProvider {
  @Override
  public Outcome charge(Payment pending) {
    return Outcome.CONFIRMED;
  }
}
