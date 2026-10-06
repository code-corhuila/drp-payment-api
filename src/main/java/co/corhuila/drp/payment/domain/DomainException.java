package co.corhuila.drp.payment.domain;

public class DomainException extends RuntimeException {
  public enum Code { INVALID_INPUT, INVALID_STATUS_TRANSITION }

  private final Code code;

  public DomainException(Code code, String message) {
    super(message);
    this.code = code;
  }

  public Code code() {
    return code;
  }
}
