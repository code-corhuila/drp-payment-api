package co.corhuila.drp.payment.adapters.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.corhuila.drp.payment.adapters.memory.MemoryIdempotency;
import co.corhuila.drp.payment.adapters.memory.MemoryOutbox;
import co.corhuila.drp.payment.adapters.memory.MemoryPayments;
import co.corhuila.drp.payment.adapters.memory.MemoryReservations;
import co.corhuila.drp.payment.adapters.security.JwksVerifier;
import co.corhuila.drp.payment.app.ReservationView;
import co.corhuila.drp.payment.app.TokenVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentHttpTest {
  private static final String KID = "spacehub-identity-2026";
  private static final String MEMBER = "44444444-4444-4444-4444-444444444444";
  private static final String RESERVATION = "22222222-2222-2222-2222-222222222222";
  private static final RSAKey RSA;

  static {
    try {
      RSA = new RSAKeyGenerator(2048).keyID(KID).generate();
    } catch (Exception ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }

  @Autowired private MockMvc mvc;
  @Autowired private MemoryReservations reservations;
  @Autowired private MemoryPayments payments;
  @Autowired private MemoryIdempotency idempotency;
  @Autowired private MemoryOutbox outbox;

  @BeforeEach
  void seed() {
    payments.clear();
    idempotency.clear();
    outbox.clear();
    reservations.clear();
    reservations.put(new ReservationView(RESERVATION, MEMBER, "PAYMENT_PENDING"));
  }

  @Test
  void healthDoesNotNeedAToken() throws Exception {
    mvc.perform(get("/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"))
        .andExpect(jsonPath("$.service").value("payment-service"));
  }

  @Test
  void listRequiresBearer() throws Exception {
    mvc.perform(get("/api/v1/payments"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.traceId").exists());
  }

  @Test
  void spoofedUserHeaderWithoutTokenIs401() throws Exception {
    mvc.perform(get("/api/v1/payments").header("X-User-Id", MEMBER))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void createWithoutIdempotencyKeyIs400() throws Exception {
    mvc.perform(
            post("/api/v1/payments")
                .header("Authorization", bearer(MEMBER, "USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(RESERVATION, 5_000_000, "COP")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void createAndListEnvelope() throws Exception {
    String token = bearer(MEMBER, "USER");
    mvc.perform(
            post("/api/v1/payments")
                .header("Authorization", token)
                .header("Idempotency-Key", "key-create-1")
                .header("X-Correlation-Id", "7a3c91e2-1111-4111-8111-000000000001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(RESERVATION, 5_000_000, "COP")))
        .andExpect(status().isCreated())
        .andExpect(header().string("X-Correlation-Id", "7a3c91e2-1111-4111-8111-000000000001"))
        .andExpect(jsonPath("$.state").value("PENDING"))
        .andExpect(jsonPath("$.amountCents").value(5_000_000))
        .andExpect(jsonPath("$.currency").value("COP"));

    mvc.perform(get("/api/v1/payments").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.meta.total").value(1))
        .andExpect(jsonPath("$.data[0].state").value("CONFIRMED"));
  }

  @Test
  void replaySameKey() throws Exception {
    String token = bearer(MEMBER, "USER");
    String payload = body(RESERVATION, 5_000_000, "COP");
    mvc.perform(
            post("/api/v1/payments")
                .header("Authorization", token)
                .header("Idempotency-Key", "key-replay-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/v1/payments")
                .header("Authorization", token)
                .header("Idempotency-Key", "key-replay-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.state").value("CONFIRMED"));
  }

  @Test
  void currencyUsdIs422() throws Exception {
    mvc.perform(
            post("/api/v1/payments")
                .header("Authorization", bearer(MEMBER, "USER"))
                .header("Idempotency-Key", "key-usd-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(RESERVATION, 5_000_000, "USD")))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.error").value("BUSINESS_RULE_VIOLATION"));
  }

  @Test
  void brokenJsonIs400() throws Exception {
    mvc.perform(
            post("/api/v1/payments")
                .header("Authorization", bearer(MEMBER, "USER"))
                .header("Idempotency-Key", "key-json-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  @Test
  void confirmRouteDoesNotExistHere() throws Exception {
    mvc.perform(
            post("/api/v1/payments/22222222-2222-2222-2222-222222222222/confirm")
                .header("Authorization", bearer(MEMBER, "USER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NOT_FOUND"));
  }

  @Test
  void limitZeroIs400() throws Exception {
    mvc.perform(get("/api/v1/payments").header("Authorization", bearer(MEMBER, "USER")).queryParam("limit", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
  }

  private static String body(String reservationId, int amountCents, String currency) {
    return "{\"reservationId\":\""
        + reservationId
        + "\",\"amountCents\":"
        + amountCents
        + ",\"currency\":\""
        + currency
        + "\"}";
  }

  private static String bearer(String sub, String role) throws Exception {
    SignedJWT jwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID).build(),
            new JWTClaimsSet.Builder()
                .subject(sub)
                .claim("role", role)
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3_600_000))
                .build());
    jwt.sign(new RSASSASigner(RSA.toPrivateKey()));
    return "Bearer " + jwt.serialize();
  }

  @TestConfiguration
  static class TestKeys {
    @Bean
    @Primary
    TokenVerifier tokens() {
      Map<String, Object> jwks = Map.of("keys", java.util.List.of(RSA.toPublicJWK().toJSONObject()));
      return new JwksVerifier(new JwksVerifier.StaticJwks(jwks));
    }
  }
}
