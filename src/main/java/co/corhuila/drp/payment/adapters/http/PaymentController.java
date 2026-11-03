package co.corhuila.drp.payment.adapters.http;

import co.corhuila.drp.payment.app.AppException;
import co.corhuila.drp.payment.app.Charges;
import co.corhuila.drp.payment.app.CreateCommand;
import co.corhuila.drp.payment.app.CreateResult;
import co.corhuila.drp.payment.app.HealthCheck;
import co.corhuila.drp.payment.app.ListFilter;
import co.corhuila.drp.payment.app.OutboxRelay;
import co.corhuila.drp.payment.app.PaymentPage;
import co.corhuila.drp.payment.app.Principal;
import co.corhuila.drp.payment.domain.PaymentState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class PaymentController {
  private static final Pattern UUID =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  private final Charges charges;
  private final OutboxRelay relay;
  private final ObjectMapper mapper;
  private final String serviceName;

  public PaymentController(
      Charges charges,
      OutboxRelay relay,
      ObjectMapper mapper,
      @Value("${SERVICE_NAME:payment-service}") String serviceName) {
    this.charges = charges;
    this.relay = relay;
    this.mapper = mapper;
    this.serviceName = serviceName;
  }

  @GetMapping("/health")
  HttpDtos.HealthDto health() {
    var h = HealthCheck.snapshot(serviceName, Instant.now());
    return new HttpDtos.HealthDto(h.status(), h.service(), HttpDtos.instant(h.timestamp()));
  }

  @GetMapping("/api/v1/payments")
  HttpDtos.ListEnvelope list(
      HttpServletRequest request,
      @RequestParam(required = false) String state,
      @RequestParam(required = false) String reservationId,
      @RequestParam(required = false) String createdFrom,
      @RequestParam(required = false) String createdTo,
      @RequestParam(required = false) String page,
      @RequestParam(required = false) String limit) {
    Principal actor = AuthFilter.principal(request);
    PaymentState parsedState = null;
    if (state != null && !state.isBlank()) {
      try {
        parsedState = PaymentState.valueOf(state);
      } catch (IllegalArgumentException ex) {
        throw AppException.validation(
            "El estado no es válido", "state", "Debe ser un estado de pago conocido");
      }
    }
    if (reservationId != null && !reservationId.isBlank() && !UUID.matcher(reservationId).matches()) {
      throw AppException.validation(
          "reservationId no es un UUID canónico",
          "reservationId",
          "Debe ser un UUID en minúsculas con guiones");
    }
    Instant from = parseInstant("createdFrom", createdFrom);
    Instant to = parseInstant("createdTo", createdTo);
    int[] paging = parsePageLimit(page, limit);
    PaymentPage got =
        charges.list(
            new ListFilter(
                actor,
                parsedState,
                blankToNull(reservationId),
                from,
                to,
                paging[0],
                paging[1]));
    return new HttpDtos.ListEnvelope(
        got.items().stream().map(HttpDtos.PaymentDto::from).toList(),
        new HttpDtos.MetaDto(got.page(), got.limit(), got.total(), got.totalPages()));
  }

  @PostMapping("/api/v1/payments")
  ResponseEntity<HttpDtos.PaymentDto> create(HttpServletRequest request, @RequestBody String raw) {
    String key = header(request, "Idempotency-Key");
    if (key.isEmpty()) {
      throw AppException.validation(
          "Idempotency-Key es obligatoria", "Idempotency-Key", "Obligatoria, 8 a 128 caracteres");
    }
    if (key.length() < 8 || key.length() > 128) {
      throw AppException.validation(
          "Idempotency-Key inválida", "Idempotency-Key", "Debe tener entre 8 y 128 caracteres");
    }
    JsonNode body;
    try {
      body = mapper.readTree(raw);
    } catch (Exception ex) {
      throw AppException.validation("JSON malformado");
    }
    if (body == null || !body.isObject()) {
      throw AppException.validation("JSON malformado");
    }
    String reservationId = text(body, "reservationId");
    if (!UUID.matcher(reservationId).matches()) {
      throw AppException.validation(
          "reservationId no es un UUID canónico",
          "reservationId",
          "Debe ser un UUID en minúsculas con guiones");
    }
    JsonNode amountNode = body.get("amountCents");
    if (amountNode == null || amountNode.isNull() || !amountNode.isIntegralNumber()) {
      throw AppException.validation(
          "amountCents debe ser un entero", "amountCents", "Debe ser un entero mayor o igual a 1");
    }
    int amountCents = amountNode.asInt();
    if (amountCents < 1) {
      throw AppException.validation(
          "amountCents debe ser mayor o igual a 1", "amountCents", "Debe ser mayor o igual a 1");
    }
    String currency = text(body, "currency");
    if (currency.isEmpty()) {
      throw AppException.validation("currency es obligatoria", "currency", "Obligatoria");
    }
    CreateResult got =
        charges.create(
            new CreateCommand(
                AuthFilter.principal(request),
                reservationId,
                amountCents,
                currency,
                key,
                JsonCanon.normalize(raw),
                AuthFilter.accessToken(request),
                CorrelationFilter.traceId(request)));
    try {
      relay.drain();
    } catch (RuntimeException ignored) {
      // Payment is already stored. The row stays unpublished for a later drain.
    }
    HttpStatus status = got.replay() ? HttpStatus.OK : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(HttpDtos.PaymentDto.from(got.payment()));
  }

  @GetMapping("/api/v1/payments/{paymentId}")
  HttpDtos.PaymentDto get(HttpServletRequest request, @PathVariable String paymentId) {
    if (!UUID.matcher(paymentId).matches()) {
      throw AppException.validation(
          "El identificador no es un UUID canónico",
          "paymentId",
          "Debe ser un UUID en minúsculas con guiones");
    }
    return HttpDtos.PaymentDto.from(charges.get(AuthFilter.principal(request), paymentId));
  }

  private static String header(HttpServletRequest request, String name) {
    String value = request.getHeader(name);
    return value == null ? "" : value.trim();
  }

  private static String text(JsonNode body, String field) {
    JsonNode node = body.get(field);
    return node == null || node.isNull() ? "" : node.asText().trim();
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static Instant parseInstant(String field, String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    if (!raw.endsWith("Z")) {
      throw AppException.validation(
          field + " debe ser RFC 3339 UTC con Z", field, "Debe terminar en Z");
    }
    try {
      return Instant.parse(raw);
    } catch (DateTimeParseException ex) {
      throw AppException.validation(field + " no es RFC 3339", field, "Debe ser RFC 3339 en UTC");
    }
  }

  private static int[] parsePageLimit(String pageRaw, String limitRaw) {
    int page = 1;
    int limit = 20;
    if (pageRaw != null && !pageRaw.isBlank()) {
      page = parsePositiveInt("page", pageRaw);
      if (page < 1) {
        throw AppException.validation(
            "El parámetro page debe ser mayor o igual a 1", "page", "Debe ser mayor o igual a 1");
      }
    }
    if (limitRaw != null && !limitRaw.isBlank()) {
      limit = parsePositiveInt("limit", limitRaw);
      if (limit < 1 || limit > 100) {
        throw AppException.validation(
            "El parámetro limit debe estar entre 1 y 100", "limit", "Debe estar entre 1 y 100");
      }
    }
    return new int[] {page, limit};
  }

  private static int parsePositiveInt(String field, String raw) {
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException ex) {
      throw AppException.validation(
          "El parámetro " + field + " debe ser un entero", field, "Debe ser un entero");
    }
  }
}
