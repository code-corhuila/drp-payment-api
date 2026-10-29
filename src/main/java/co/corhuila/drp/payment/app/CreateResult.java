package co.corhuila.drp.payment.app;

import co.corhuila.drp.payment.domain.Payment;

public record CreateResult(Payment payment, boolean replay) {}
