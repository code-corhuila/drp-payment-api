package co.corhuila.drp.payment.adapters.postgres;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import javax.sql.DataSource;

public final class PostgresDataSource {
  private PostgresDataSource() {}

  public static DataSource open(String databaseUrl) {
    HikariConfig cfg = new HikariConfig();
    if (databaseUrl.startsWith("jdbc:")) {
      cfg.setJdbcUrl(databaseUrl);
      return new HikariDataSource(cfg);
    }
    URI uri = URI.create(databaseUrl.replaceFirst("^postgres(ql)?:", "http:"));
    String userInfo = uri.getUserInfo();
    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
    String path = uri.getPath() == null || uri.getPath().isBlank() ? "/drp" : uri.getPath();
    String query = uri.getQuery() == null ? "sslmode=disable" : uri.getQuery();
    cfg.setJdbcUrl("jdbc:postgresql://" + uri.getHost() + ":" + port + path + "?" + query);
    if (userInfo != null && !userInfo.isBlank()) {
      String[] parts = userInfo.split(":", 2);
      cfg.setUsername(parts[0]);
      if (parts.length > 1) {
        cfg.setPassword(parts[1]);
      }
    }
    cfg.setMaximumPoolSize(8);
    return new HikariDataSource(cfg);
  }
}
