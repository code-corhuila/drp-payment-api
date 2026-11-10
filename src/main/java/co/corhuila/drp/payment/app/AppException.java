package co.corhuila.drp.payment.app;

import java.util.List;
import java.util.Map;

/** Application error mapped to the single API envelope. Domain stays free of HTTP. */
public final class AppException extends RuntimeException {
  public enum Kind {
    VALIDATION,
    UNAUTHORIZED,
    NOT_FOUND,
    CONFLICT,
    BUSINESS,
    TRANSITION,
    UNAVAILABLE,
    INTERNAL
  }

  private final Kind kind;
  private final String code;
  private final List<Map<String, String>> details;

  private AppException(Kind kind, String code, String message, List<Map<String, String>> details) {
    super(message);
    this.kind = kind;
    this.code = code;
    this.details = details;
  }

  public Kind kind() {
    return kind;
  }

  public String code() {
    return code;
  }

  public List<Map<String, String>> details() {
    return details;
  }

  public static AppException validation(String message, String field, String fieldMessage) {
    return new AppException(
        Kind.VALIDATION,
        "VALIDATION_ERROR",
        message,
        List.of(Map.of("field", field, "message", fieldMessage)));
  }

  public static AppException validation(String message) {
    return new AppException(Kind.VALIDATION, "VALIDATION_ERROR", message, null);
  }

  public static AppException unauthorized() {
    return new AppException(Kind.UNAUTHORIZED, "UNAUTHORIZED", "Token de autenticación requerido", null);
  }

  public static AppException notFound(String message) {
    return new AppException(Kind.NOT_FOUND, "NOT_FOUND", message, null);
  }

  public static AppException conflict() {
    return new AppException(
        Kind.CONFLICT, "IDEMPOTENCY_CONFLICT", "La misma Idempotency-Key se usó con otro cuerpo", null);
  }

  public static AppException business(String message) {
    return new AppException(Kind.BUSINESS, "BUSINESS_RULE_VIOLATION", message, null);
  }

  public static AppException transition(String message) {
    return new AppException(Kind.TRANSITION, "INVALID_STATUS_TRANSITION", message, null);
  }

  public static AppException unavailable(String message) {
    return new AppException(Kind.UNAVAILABLE, "SERVICE_UNAVAILABLE", message, null);
  }

  public static AppException jwksUnavailable() {
    return unavailable("No se pudo validar el token");
  }
}
