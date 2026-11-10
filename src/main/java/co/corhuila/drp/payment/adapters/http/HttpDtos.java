package co.corhuila.drp.payment.adapters.http;

import co.corhuila.drp.payment.domain.Payment;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

final class HttpDtos {
  static final DateTimeFormatter INSTANT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  private HttpDtos() {}

  static String instant(Instant value) {
    return INSTANT.format(value);
  }

  record PaymentDto(
      String id,
      String reservationId,
      int amountCents,
      String currency,
      String state,
      String idempotencyKey,
      String providerReference,
      String createdAt,
      String updatedAt) {
    static PaymentDto from(Payment p) {
      return new PaymentDto(
          p.id(),
          p.reservationId(),
          p.amountCents(),
          p.currency(),
          p.state().name(),
          p.idempotencyKey(),
          p.providerReference(),
          instant(p.createdAt()),
          instant(p.updatedAt()));
    }
  }

  record MetaDto(int page, int limit, int total, int totalPages) {}

  record ListEnvelope(List<PaymentDto> data, MetaDto meta) {}

  record HealthDto(String status, String service, String timestamp) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  record ErrorBody(String error, String message, List<Map<String, String>> details, String traceId) {}
}
