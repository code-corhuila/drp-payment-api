package co.corhuila.drp.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaymentApplication {
  public static void main(String[] args) {
    String addr = System.getenv("HTTP_ADDR");
    if (addr != null && addr.startsWith(":")) {
      System.setProperty("server.port", addr.substring(1));
    }
    SpringApplication.run(PaymentApplication.class, args);
  }
}
