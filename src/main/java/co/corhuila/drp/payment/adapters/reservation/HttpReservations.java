package co.corhuila.drp.payment.adapters.reservation;

import co.corhuila.drp.payment.app.AppException;
import co.corhuila.drp.payment.app.ReservationCatalog;
import co.corhuila.drp.payment.app.ReservationView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/** Reads E-13 over HTTP. Never opens reservation tables (Anexo J). */
public final class HttpReservations implements ReservationCatalog {
  private final String baseUrl;
  private final HttpClient client;
  private final ObjectMapper mapper;

  public HttpReservations(String baseUrl, HttpClient client, ObjectMapper mapper) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  public Optional<ReservationView> lookup(String reservationId, String accessToken, String correlationId) {
    try {
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/reservations/" + reservationId))
              .timeout(Duration.ofSeconds(3))
              .GET()
              .header("Authorization", "Bearer " + accessToken);
      if (correlationId != null && !correlationId.isBlank()) {
        builder.header("X-Correlation-Id", correlationId);
      }
      HttpResponse<String> res = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() == 404) {
        return Optional.empty();
      }
      if (res.statusCode() != 200) {
        throw AppException.unavailable("reservation-service no respondió");
      }
      JsonNode node = mapper.readTree(res.body());
      String id = text(node, "id");
      String userId = text(node, "userId");
      String state = text(node, "state");
      if (id.isBlank() || userId.isBlank() || state.isBlank()) {
        throw AppException.unavailable("reservation-service no respondió");
      }
      return Optional.of(new ReservationView(id, userId, state));
    } catch (AppException ex) {
      throw ex;
    } catch (Exception ex) {
      throw AppException.unavailable("reservation-service no respondió");
    }
  }

  private static String text(JsonNode node, String field) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() ? "" : value.asText();
  }
}
