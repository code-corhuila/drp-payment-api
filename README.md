# drp-payment-api

Payment HTTP API (**Java 21**, course `api-java` / Spring Boot). **Hexagonal:** `co.corhuila.drp.payment.domain` has no Spring/JPA/HTTP imports. DDL lives in [`drp-payment-db`](https://github.com/code-corhuila/drp-payment-db). Engine: [`drp-infra-postgres`](https://github.com/code-corhuila/drp-infra-postgres).

Walking HTTP for E-15…E-17 (`:8084`): lists `{data, meta}`, create with `Idempotency-Key` returns **201 `PENDING`**, replay is **200**. The MVP provider confirms immediately and appends `PaymentConfirmed` to an **in-memory outbox** in the same use case (D-C14: this service never `POST`s a reservation confirm). RS256 against identity JWKS. Reservation lookup is HTTP to `drp-reservation-api` when `RESERVATION_API_URL` is set; otherwise an in-memory catalog (tests). Publisher + `payment.outbox` Flyway come with Postgres.

```bash
mvn -B test
mvn -B spring-boot:run
```

Child of `develop` named `hu-c3-*-dev`. Promote with `cherry-pick -x`.
