package co.corhuila.drp.payment.app;

public interface TokenVerifier {
  Principal parse(String raw);
}
