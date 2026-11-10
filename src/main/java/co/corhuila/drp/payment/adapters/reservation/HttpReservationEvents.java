package co.corhuila.drp.payment.adapters.reservation;

import co.corhuila.drp.payment.app.EventPublisher;
import co.corhuila.drp.payment.app.OutboxEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Walking hop: POST /internal/events on reservation. Not the public API. Not on the gateway. */
public final class HttpReservationEvents implements EventPublisher {
  private final String baseUrl;
  private final String token;
  private final HttpClient client;
  private final ObjectMapper mapper;

  public HttpReservationEvents(String baseUrl, String token, HttpClient client, ObjectMapper mapper) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.token = token;
    this.client = client;
    this.mapper = mapper;
  }

  @Override
  public void publish(OutboxEvent event) {
    try {
      ObjectNode root = mapper.createObjectNode();
      root.put("eventId", event.eventId());
      root.put("eventType", event.eventType());
      if (event.correlationId() != null && !event.correlationId().isBlank()) {
        root.put("correlationId", event.correlationId());
      }
      root.set("payload", mapper.readTree(event.payloadJson()));
      HttpRequest.Builder builder =
          HttpRequest.newBuilder(URI.create(baseUrl + "/internal/events"))
              .timeout(Duration.ofSeconds(3))
              .header("Content-Type", "application/json")
              .header("X-Internal-Token", token)
              .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root)));
      if (event.correlationId() != null && !event.correlationId().isBlank()) {
        builder.header("X-Correlation-Id", event.correlationId());
      }
      HttpResponse<String> res = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (res.statusCode() < 200 || res.statusCode() >= 300) {
        throw new IllegalStateException("reservation consume HTTP " + res.statusCode());
      }
    } catch (RuntimeException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new IllegalStateException("reservation consume failed", ex);
    }
  }
}
