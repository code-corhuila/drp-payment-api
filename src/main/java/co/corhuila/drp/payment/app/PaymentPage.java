package co.corhuila.drp.payment.app;

import co.corhuila.drp.payment.domain.Payment;
import java.util.List;

public record PaymentPage(List<Payment> items, int page, int limit, int total, int totalPages) {}
