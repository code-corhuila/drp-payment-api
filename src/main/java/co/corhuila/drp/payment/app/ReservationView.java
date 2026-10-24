package co.corhuila.drp.payment.app;

/** Copy of a reservation the payment context is allowed to see. Not a cross-schema join. */
public record ReservationView(String id, String userId, String state) {}
