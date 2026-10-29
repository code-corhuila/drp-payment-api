package co.corhuila.drp.payment.app;

import java.time.Instant;

public final class HealthCheck {
  private HealthCheck() {}

  public static Health snapshot(String service, Instant now) {
    String name = (service == null || service.isBlank()) ? "payment-service" : service;
    Instant at = now == null ? Instant.now() : now;
    return new Health("ok", name, at);
  }

  public record Health(String status, String service, Instant timestamp) {}
}
