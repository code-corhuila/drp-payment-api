# drp-payment-api

Payment HTTP API (**Java 21**, Anexo J.1.2). **Hexagonal:** `co.corhuila.drp.payment.domain` has no Spring/JPA/HTTP imports. DDL lives in [`drp-payment-db`](https://github.com/code-corhuila/drp-payment-db). Engine: [`drp-infra-postgres`](https://github.com/code-corhuila/drp-infra-postgres).

This increment is the **Payment aggregate + Money VO** (`amountCents`, unique `idempotencyKey`, states `PENDING | CONFIRMED | FAILED`). HTTP adapters come in a later `feat/`.

```bash
mvn -B test
```

Child of `develop` named `feat/…`. Promote with `cherry-pick -x`.
