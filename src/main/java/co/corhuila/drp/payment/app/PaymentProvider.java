package co.corhuila.drp.payment.app;

import co.corhuila.drp.payment.domain.Payment;

public interface PaymentProvider {
  Result charge(Payment pending);

  enum Outcome {
    CONFIRMED,
    FAILED
  }

  record Result(Outcome outcome, String providerReference) {
    public static Result confirmed(String providerReference) {
      if (providerReference == null || providerReference.isBlank()) {
        throw new IllegalArgumentException("provider evidence required");
      }
      return new Result(Outcome.CONFIRMED, providerReference);
    }

    public static Result failed() {
      return new Result(Outcome.FAILED, null);
    }
  }
}
