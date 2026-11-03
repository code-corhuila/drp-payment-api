package co.corhuila.drp.payment.adapters.http;

import co.corhuila.drp.payment.app.AppException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public final class ApiExceptionHandler {

  @ExceptionHandler(AppException.class)
  ResponseEntity<HttpDtos.ErrorBody> app(AppException ex, HttpServletRequest request) {
    return envelope(statusOf(ex), ex.code(), ex.getMessage(), ex.details(), request);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<HttpDtos.ErrorBody> brokenJson(HttpServletRequest request) {
    return envelope(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "JSON malformado", null, request);
  }

  @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
  ResponseEntity<HttpDtos.ErrorBody> missing(HttpServletRequest request) {
    return envelope(HttpStatus.NOT_FOUND, "NOT_FOUND", "Ruta no encontrada", null, request);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<HttpDtos.ErrorBody> unexpected(HttpServletRequest request) {
    return envelope(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Error interno", null, request);
  }

  private static ResponseEntity<HttpDtos.ErrorBody> envelope(
      HttpStatus status,
      String code,
      String message,
      List<Map<String, String>> details,
      HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(new HttpDtos.ErrorBody(code, message, details, CorrelationFilter.traceId(request)));
  }

  private static HttpStatus statusOf(AppException ex) {
    return switch (ex.kind()) {
      case VALIDATION -> HttpStatus.BAD_REQUEST;
      case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
      case NOT_FOUND -> HttpStatus.NOT_FOUND;
      case CONFLICT -> HttpStatus.CONFLICT;
      case BUSINESS, TRANSITION -> HttpStatus.UNPROCESSABLE_ENTITY;
      case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
      case INTERNAL -> HttpStatus.INTERNAL_SERVER_ERROR;
    };
  }
}
