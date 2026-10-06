package co.corhuila.drp.payment.domain;

/** Integer minor units (norma 5.3.9). Never a float. */
public record Money(int amountCents, String currency) {
  public Money {
    if (amountCents < 1) {
      throw new DomainException(DomainException.Code.INVALID_INPUT, "amountCents must be > 0");
    }
    if (currency == null || currency.length() != 3) {
      throw new DomainException(DomainException.Code.INVALID_INPUT, "currency must be ISO-4217");
    }
    currency = currency.toUpperCase();
  }

  public static Money cop(int amountCents) {
    return new Money(amountCents, "COP");
  }
}
