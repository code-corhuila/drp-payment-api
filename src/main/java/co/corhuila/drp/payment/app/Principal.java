package co.corhuila.drp.payment.app;

public record Principal(String subject, String role) {
  public boolean admin() {
    return "ADMIN".equals(role);
  }
}
