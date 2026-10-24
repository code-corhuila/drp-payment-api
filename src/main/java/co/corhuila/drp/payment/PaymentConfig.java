package co.corhuila.drp.payment;

import co.corhuila.drp.payment.adapters.memory.MemoryIdempotency;
import co.corhuila.drp.payment.adapters.memory.MemoryOutbox;
import co.corhuila.drp.payment.adapters.memory.MemoryPayments;
import co.corhuila.drp.payment.adapters.memory.MemoryReservations;
import co.corhuila.drp.payment.adapters.provider.ImmediateConfirmProvider;
import co.corhuila.drp.payment.adapters.reservation.HttpReservations;
import co.corhuila.drp.payment.adapters.security.JwksVerifier;
import co.corhuila.drp.payment.app.Charges;
import co.corhuila.drp.payment.app.ReservationCatalog;
import co.corhuila.drp.payment.app.TokenVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

  @Bean
  MemoryPayments memoryPayments() {
    return new MemoryPayments();
  }

  @Bean
  MemoryIdempotency memoryIdempotency() {
    return new MemoryIdempotency();
  }

  @Bean
  MemoryOutbox memoryOutbox() {
    return new MemoryOutbox();
  }

  @Bean
  MemoryReservations memoryReservations() {
    return new MemoryReservations();
  }

  @Bean
  TokenVerifier tokenVerifier(
      ObjectMapper mapper,
      @Value("${IDENTITY_JWKS_URL:http://identity-api:8081/api/v1/auth/jwks}") String url) {
    return JwksVerifier.http(url, httpClient(), mapper);
  }

  @Bean
  Charges charges(
      MemoryPayments payments,
      MemoryIdempotency idempotency,
      MemoryReservations memoryReservations,
      MemoryOutbox outbox,
      ObjectMapper mapper,
      @Value("${RESERVATION_API_URL:}") String reservationUrl) {
    ReservationCatalog catalog = memoryReservations;
    if (reservationUrl != null && !reservationUrl.isBlank()) {
      catalog = new HttpReservations(reservationUrl, httpClient(), mapper);
    }
    return new Charges(
        payments,
        idempotency,
        catalog,
        new ImmediateConfirmProvider(),
        outbox,
        Instant::now,
        () -> UUID.randomUUID().toString());
  }

  private static HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }
}
