package co.corhuila.drp.payment.app;

import co.corhuila.drp.payment.domain.DomainException;
import co.corhuila.drp.payment.domain.Money;
import co.corhuila.drp.payment.domain.Payment;
import co.corhuila.drp.payment.domain.PaymentState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * E-15 / E-16 / E-17. Confirm is not an HTTP command: the simulated provider writes
 * PaymentConfirmed to the outbox in the same use case (D-C14, DP-02).
 */
public final class Charges {
  public static final String CREATE_ROUTE = "/api/v1/payments";

  private final PaymentRepository payments;
  private final IdempotencyStore idempotency;
  private final ReservationCatalog reservations;
  private final PaymentProvider provider;
  private final Outbox outbox;
  private final Supplier<Instant> clock;
  private final Supplier<String> ids;

  public Charges(
      PaymentRepository payments,
      IdempotencyStore idempotency,
      ReservationCatalog reservations,
      PaymentProvider provider,
      Outbox outbox,
      Supplier<Instant> clock,
      Supplier<String> ids) {
    this.payments = payments;
    this.idempotency = idempotency;
    this.reservations = reservations;
    this.provider = provider;
    this.outbox = outbox;
    this.clock = clock == null ? Instant::now : clock;
    this.ids = ids == null ? () -> UUID.randomUUID().toString() : ids;
  }

  public PaymentPage list(ListFilter filter) {
    if (filter.page() < 1 || filter.limit() < 1 || filter.limit() > 100) {
      throw AppException.validation("Parámetros de paginación inválidos");
    }
    List<Payment> matched = new ArrayList<>();
    for (StoredPayment row : payments.list()) {
      Payment p = row.payment();
      if (!filter.actor().admin() && !row.ownerUserId().equals(filter.actor().subject())) {
        continue;
      }
      if (filter.state() != null && p.state() != filter.state()) {
        continue;
      }
      if (filter.reservationId() != null && !p.reservationId().equals(filter.reservationId())) {
        continue;
      }
      if (filter.createdFrom() != null && p.createdAt().isBefore(filter.createdFrom())) {
        continue;
      }
      if (filter.createdTo() != null && !p.createdAt().isBefore(filter.createdTo())) {
        continue;
      }
      matched.add(p);
    }
    matched.sort(
        Comparator.comparing(Payment::createdAt)
            .reversed()
            .thenComparing(Payment::id, Comparator.reverseOrder()));
    int total = matched.size();
    int totalPages = total == 0 ? 0 : (total + filter.limit() - 1) / filter.limit();
    int start = (filter.page() - 1) * filter.limit();
    if (start >= total) {
      return new PaymentPage(List.of(), filter.page(), filter.limit(), total, totalPages);
    }
    int end = Math.min(start + filter.limit(), total);
    return new PaymentPage(matched.subList(start, end), filter.page(), filter.limit(), total, totalPages);
  }

  public Payment get(Principal actor, String id) {
    StoredPayment row =
        payments.findById(id).orElseThrow(() -> AppException.notFound("El pago no existe"));
    if (!actor.admin() && !row.ownerUserId().equals(actor.subject())) {
      throw AppException.notFound("El pago no existe");
    }
    return row.payment();
  }

  public CreateResult create(CreateCommand cmd) {
    if (cmd.idempotencyKey() == null
        || cmd.idempotencyKey().length() < 8
        || cmd.idempotencyKey().length() > 128) {
      throw AppException.validation(
          "Idempotency-Key inválida", "Idempotency-Key", "Debe tener entre 8 y 128 caracteres");
    }
    Optional<IdempotencyStore.Replay> replay =
        idempotency.get(cmd.actor().subject(), CREATE_ROUTE, cmd.idempotencyKey());
    if (replay.isPresent()) {
      if (!replay.get().normalizedBody().equals(cmd.normalizedBody())) {
        throw AppException.conflict();
      }
      return new CreateResult(replay.get().stored().payment(), true);
    }

    ReservationView reservation =
        reservations
            .lookup(cmd.reservationId(), cmd.accessToken(), cmd.correlationId())
            .orElseThrow(() -> AppException.notFound("La reserva no existe"));
    if (!reservation.userId().equals(cmd.actor().subject())) {
      throw AppException.notFound("La reserva no existe");
    }
    if (!"PAYMENT_PENDING".equals(reservation.state())) {
      throw AppException.transition("La reserva no puede pagarse en el estado actual");
    }
    if (!"COP".equals(cmd.currency())) {
      throw AppException.business("La moneda debe ser COP.");
    }

    Optional<StoredPayment> open = payments.findOpenByReservation(cmd.reservationId());
    if (open.isPresent() && !open.get().payment().sameIdempotencyKey(cmd.idempotencyKey())) {
      throw AppException.business("La reserva ya tiene un pago en curso o confirmado.");
    }

    Instant now = clock.get();
    Payment pending;
    try {
      pending =
          Payment.pending(
              ids.get(),
              cmd.reservationId(),
              Money.cop(cmd.amountCents()),
              cmd.idempotencyKey(),
              now);
    } catch (DomainException ex) {
      throw AppException.validation(ex.getMessage());
    }
    payments.save(new StoredPayment(pending, cmd.actor().subject()));

    Payment after = settle(pending, now, cmd.correlationId(), cmd.actor().subject());
    payments.save(new StoredPayment(after, cmd.actor().subject()));
    idempotency.put(
        cmd.actor().subject(),
        CREATE_ROUTE,
        cmd.idempotencyKey(),
        new IdempotencyStore.Replay(cmd.normalizedBody(), new StoredPayment(pending, cmd.actor().subject())));
    return new CreateResult(pending, false);
  }

  private Payment settle(Payment pending, Instant now, String correlationId, String userId) {
    PaymentProvider.Outcome outcome = provider.charge(pending);
    if (outcome == PaymentProvider.Outcome.FAILED) {
      Payment failed = pending.fail(now);
      outbox.append(event(failed, OutboxEvent.PAYMENT_FAILED, OutboxEvent.TOPIC_FAILED, correlationId, userId));
      return failed;
    }
    Payment confirmed = pending.confirm("sim-" + pending.id().substring(0, 8), now);
    outbox.append(
        event(confirmed, OutboxEvent.PAYMENT_CONFIRMED, OutboxEvent.TOPIC_CONFIRMED, correlationId, userId));
    return confirmed;
  }

  private OutboxEvent event(Payment payment, String type, String topic, String correlationId, String userId) {
    String payload =
        """
        {"paymentId":"%s","reservationId":"%s","amountCents":%d,"currency":"%s","userId":"%s"}
        """
            .formatted(
                payment.id(),
                payment.reservationId(),
                payment.amountCents(),
                payment.currency(),
                userId)
            .strip();
    return new OutboxEvent(
        ids.get(),
        type,
        payment.id(),
        "Payment",
        topic,
        clock.get(),
        1,
        payload,
        correlationId,
        false);
  }
}
