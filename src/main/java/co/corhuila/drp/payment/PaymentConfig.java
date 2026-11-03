package co.corhuila.drp.payment;

import co.corhuila.drp.payment.adapters.memory.MemoryIdempotency;
import co.corhuila.drp.payment.adapters.memory.MemoryOutbox;
import co.corhuila.drp.payment.adapters.memory.MemoryPayments;
import co.corhuila.drp.payment.adapters.memory.MemoryReservations;
import co.corhuila.drp.payment.adapters.provider.ImmediateConfirmProvider;
import co.corhuila.drp.payment.adapters.notification.HttpNotificationEvents;
import co.corhuila.drp.payment.adapters.reservation.HttpReservationEvents;
import co.corhuila.drp.payment.adapters.reservation.HttpReservations;
import co.corhuila.drp.payment.app.FanoutPublisher;
import co.corhuila.drp.payment.adapters.security.JwksVerifier;
import co.corhuila.drp.payment.adapters.postgres.JdbcOutbox;
import co.corhuila.drp.payment.adapters.postgres.JdbcPayments;
import co.corhuila.drp.payment.adapters.postgres.PostgresDataSource;
import co.corhuila.drp.payment.app.Charges;
import co.corhuila.drp.payment.app.EventPublisher;
import co.corhuila.drp.payment.app.Outbox;
import co.corhuila.drp.payment.app.OutboxRelay;
import co.corhuila.drp.payment.app.PaymentRepository;
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
import javax.sql.DataSource;
import org.springframework.context.annotation.Primary;

@Configuration
public class PaymentConfig {

  private DataSource jdbc;

  private DataSource jdbc() {
    if (jdbc != null) {
      return jdbc;
    }
    String dsn = System.getenv("DATABASE_URL");
    if (dsn == null || dsn.isBlank()) {
      return null;
    }
    jdbc = PostgresDataSource.open(dsn);
    return jdbc;
  }

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
  @Primary
  PaymentRepository paymentStore(MemoryPayments memory) {
    DataSource ds = jdbc();
    if (ds == null) {
      return memory;
    }
    return new JdbcPayments(ds);
  }

  @Bean
  @Primary
  Outbox outboxStore(MemoryOutbox memory) {
    DataSource ds = jdbc();
    if (ds == null) {
      return memory;
    }
    return new JdbcOutbox(ds);
  }

  @Bean
  Charges charges(
      PaymentRepository payments,
      MemoryIdempotency idempotency,
      MemoryReservations memoryReservations,
      Outbox outbox,
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

  @Bean
  EventPublisher eventPublisher(
      ObjectMapper mapper,
      @Value("${RESERVATION_API_URL:}") String reservationUrl,
      @Value("${NOTIFICATION_API_URL:}") String notificationUrl,
      @Value("${PAYMENT_INTERNAL_TOKEN:}") String paymentToken,
      @Value("${NOTIFICATION_INTERNAL_TOKEN:}") String notificationToken,
      @Value("${INTERNAL_EVENT_TOKEN:}") String fallbackToken) {
    String toReservation = firstToken(paymentToken, fallbackToken);
    String toNotification = firstToken(notificationToken, fallbackToken);
    if (reservationUrl == null || reservationUrl.isBlank() || toReservation.isBlank()) {
      return EventPublisher.NOOP;
    }
    EventPublisher reservation =
        new HttpReservationEvents(reservationUrl, toReservation, httpClient(), mapper);
    if (notificationUrl == null || notificationUrl.isBlank() || toNotification.isBlank()) {
      return reservation;
    }
    return new FanoutPublisher(
        reservation,
        new HttpNotificationEvents(notificationUrl, toNotification, httpClient(), mapper));
  }

  private static String firstToken(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return "";
  }

  @Bean
  OutboxRelay outboxRelay(Outbox outbox, EventPublisher publisher) {
    return new OutboxRelay(outbox, publisher);
  }

  private static HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
  }
}
