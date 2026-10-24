package co.corhuila.drp.payment.app;

import co.corhuila.drp.payment.domain.Payment;

/** Persistence record: aggregate plus owner copy (the JWT sub). Schema has no user_id yet. */
public record StoredPayment(Payment payment, String ownerUserId) {}
